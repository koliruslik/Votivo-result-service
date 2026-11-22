package com.votivo.result.repository;

import com.votivo.result.api.PollResult;
import com.votivo.result.domain.PollSnapshot;
import com.votivo.result.domain.PollState;
import com.votivo.result.domain.VoteAnswer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL storage. The counters move with {@code votes = votes + ?} inside one statement, so concurrent
 * consumers cannot lose an update; rows created by a vote that overtook its poll.started are completed by
 * {@link #saveStructure}, which never resets a counter.
 */
@Repository
public class JdbcResultRepository implements ResultRepository {
    private final JdbcClient jdbc;

    public JdbcResultRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(UUID eventId) {
        return jdbc.sql("INSERT INTO processed_events (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING")
                .param(eventId).update() == 1;
    }

    @Override
    public void saveStructure(PollSnapshot poll) {
        jdbc.sql("""
                INSERT INTO poll_results (poll_id, title) VALUES (?, ?)
                ON CONFLICT (poll_id) DO UPDATE SET title = EXCLUDED.title""")
                .param(poll.pollId()).param(poll.title()).update();
        int questionPosition = 0;
        for (PollSnapshot.Question question : poll.questions()) {
            jdbc.sql("""
                    INSERT INTO question_results (question_id, poll_id, text, multiple, position) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (question_id) DO UPDATE
                    SET text = EXCLUDED.text, multiple = EXCLUDED.multiple, position = EXCLUDED.position""")
                    .param(question.id()).param(poll.pollId()).param(question.text())
                    .param(question.multiple()).param(questionPosition++).update();
            int optionPosition = 0;
            for (PollSnapshot.Option option : question.options()) {
                jdbc.sql("""
                        INSERT INTO option_results (option_id, question_id, text, position) VALUES (?, ?, ?, ?)
                        ON CONFLICT (option_id) DO UPDATE SET text = EXCLUDED.text, position = EXCLUDED.position""")
                        .param(option.id()).param(question.id()).param(option.text()).param(optionPosition++).update();
            }
        }
    }

    @Override
    public void setState(UUID pollId, PollState state) {
        jdbc.sql("""
                INSERT INTO poll_results (poll_id, status) VALUES (?, ?)
                ON CONFLICT (poll_id) DO UPDATE SET status = EXCLUDED.status""")
                .param(pollId).param(state.name()).update();
    }

    @Override
    public void addVote(UUID pollId, List<VoteAnswer> answers, int delta) {
        jdbc.sql("""
                INSERT INTO poll_results (poll_id, total_votes) VALUES (?, ?)
                ON CONFLICT (poll_id) DO UPDATE SET total_votes = poll_results.total_votes + EXCLUDED.total_votes""")
                .param(pollId).param(delta).update();
        for (VoteAnswer answer : answers) {
            jdbc.sql("INSERT INTO question_results (question_id, poll_id) VALUES (?, ?) ON CONFLICT (question_id) DO NOTHING")
                    .param(answer.questionId()).param(pollId).update();
            for (UUID optionId : answer.optionIds()) {
                jdbc.sql("""
                        INSERT INTO option_results (option_id, question_id, votes) VALUES (?, ?, ?)
                        ON CONFLICT (option_id) DO UPDATE SET votes = option_results.votes + EXCLUDED.votes""")
                        .param(optionId).param(answer.questionId()).param((long) delta).update();
            }
        }
    }

    @Override
    public Optional<PollResult> find(UUID pollId) {
        record Header(String title, String status, long totalVotes) { }
        Optional<Header> header = jdbc.sql("SELECT title, status, total_votes FROM poll_results WHERE poll_id = ?")
                .param(pollId)
                .query((rs, row) -> new Header(rs.getString("title"), rs.getString("status"), rs.getLong("total_votes")))
                .optional();
        if (header.isEmpty()) return Optional.empty();

        Map<UUID, PollResult.QuestionResult> questions = new LinkedHashMap<>();
        Map<UUID, List<PollResult.OptionResult>> options = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT q.question_id, q.text AS question_text, q.multiple, o.option_id, o.text AS option_text, o.votes
                FROM question_results q LEFT JOIN option_results o ON o.question_id = q.question_id
                WHERE q.poll_id = ?
                ORDER BY q.position, q.question_id, o.position, o.option_id""")
                .param(pollId)
                .query(rs -> {
                    UUID questionId = rs.getObject("question_id", UUID.class);
                    options.computeIfAbsent(questionId, id -> new ArrayList<>());
                    questions.putIfAbsent(questionId, new PollResult.QuestionResult(questionId,
                            rs.getString("question_text"), rs.getBoolean("multiple"), null));
                    UUID optionId = rs.getObject("option_id", UUID.class);
                    if (optionId != null) {
                        options.get(questionId).add(new PollResult.OptionResult(optionId, rs.getString("option_text"),
                                rs.getLong("votes")));
                    }
                });
        List<PollResult.QuestionResult> result = questions.values().stream()
                .map(q -> new PollResult.QuestionResult(q.questionId(), q.text(), q.multiple(), options.get(q.questionId())))
                .toList();
        Header h = header.get();
        return Optional.of(new PollResult(pollId, h.title(), h.status(), h.totalVotes(), result));
    }
}
