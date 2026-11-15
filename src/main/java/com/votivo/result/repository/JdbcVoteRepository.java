package com.votivo.result.repository;

import com.votivo.result.domain.VoteSubmission;
import com.votivo.result.domain.VoteSubmission.Answer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcVoteRepository implements VoteRepository {
    private final JdbcTemplate jdbc;

    public JdbcVoteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<VoteSubmission> findByPollId(UUID pollId) {
        List<SelectionRow> rows = jdbc.query("""
                SELECT v.vote_id, s.question_id, s.option_id
                FROM result_vote v
                JOIN result_selection s ON s.vote_id = v.vote_id
                WHERE v.poll_id = ?
                ORDER BY v.vote_id, s.question_id, s.option_id
                """, (rs, rowNumber) -> new SelectionRow(
                        rs.getObject("vote_id", UUID.class),
                        rs.getObject("question_id", UUID.class),
                        rs.getObject("option_id", UUID.class)), pollId);

        Map<UUID, List<SelectionRow>> byVote = rows.stream().collect(
                Collectors.groupingBy(SelectionRow::voteId, LinkedHashMap::new, Collectors.toList()));
        return byVote.entrySet().stream().map(entry -> {
            Map<UUID, List<SelectionRow>> byQuestion = entry.getValue().stream().collect(
                    Collectors.groupingBy(SelectionRow::questionId, LinkedHashMap::new, Collectors.toList()));
            List<Answer> answers = byQuestion.entrySet().stream()
                    .map(question -> new Answer(question.getKey(), question.getValue().stream()
                            .map(SelectionRow::optionId).collect(Collectors.toSet())))
                    .toList();
            return new VoteSubmission(entry.getKey(), pollId, answers);
        }).toList();
    }

    @Override
    @Transactional
    public void save(VoteSubmission vote) {
        int inserted = jdbc.update("""
                INSERT INTO result_vote (vote_id, poll_id) VALUES (?, ?)
                ON CONFLICT (vote_id) DO NOTHING
                """, vote.voteId(), vote.pollId());
        if (inserted == 0) {
            return;
        }

        jdbc.batchUpdate(
                "INSERT INTO result_answer (vote_id, question_id) VALUES (?, ?)",
                vote.answers(), vote.answers().size(),
                (statement, answer) -> {
                    statement.setObject(1, vote.voteId());
                    statement.setObject(2, answer.questionId());
                });
        List<SelectionRow> selections = vote.answers().stream()
                .flatMap(answer -> answer.optionIds().stream()
                        .map(option -> new SelectionRow(vote.voteId(), answer.questionId(), option)))
                .toList();
        jdbc.batchUpdate(
                "INSERT INTO result_selection (vote_id, question_id, option_id) VALUES (?, ?, ?)",
                selections, selections.size(),
                (statement, row) -> {
                    statement.setObject(1, row.voteId());
                    statement.setObject(2, row.questionId());
                    statement.setObject(3, row.optionId());
                });
    }

    private record SelectionRow(UUID voteId, UUID questionId, UUID optionId) {}
}
