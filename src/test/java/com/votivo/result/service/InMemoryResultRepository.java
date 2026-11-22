package com.votivo.result.service;

import com.votivo.result.api.PollResult;
import com.votivo.result.domain.PollSnapshot;
import com.votivo.result.domain.PollState;
import com.votivo.result.domain.VoteAnswer;
import com.votivo.result.repository.ResultRepository;
import java.util.*;

/** Test double with the semantics of ResultRepository: structure never resets counters, a vote can come first. */
class InMemoryResultRepository implements ResultRepository {
    private final Set<UUID> processed = new HashSet<>();
    private final Map<UUID, PollState> states = new HashMap<>();
    private final Map<UUID, String> titles = new HashMap<>();
    private final Map<UUID, Long> totals = new HashMap<>();
    private final Map<UUID, List<UUID>> questionsOfPoll = new LinkedHashMap<>();
    private final Map<UUID, String> questionText = new HashMap<>();
    private final Map<UUID, List<UUID>> optionsOfQuestion = new LinkedHashMap<>();
    private final Map<UUID, String> optionText = new HashMap<>();
    private final Map<UUID, Long> votes = new HashMap<>();
    int rejectedByFailure;
    boolean failOnVote;

    @Override public boolean markProcessed(UUID eventId) { return processed.add(eventId); }

    @Override public void saveStructure(PollSnapshot poll) {
        titles.put(poll.pollId(), poll.title());
        states.putIfAbsent(poll.pollId(), PollState.ACTIVE);
        for (PollSnapshot.Question q : poll.questions()) {
            questionsOfPoll.computeIfAbsent(poll.pollId(), k -> new ArrayList<>());
            if (!questionsOfPoll.get(poll.pollId()).contains(q.id())) questionsOfPoll.get(poll.pollId()).add(q.id());
            questionText.put(q.id(), q.text());
            for (PollSnapshot.Option o : q.options()) {
                optionsOfQuestion.computeIfAbsent(q.id(), k -> new ArrayList<>());
                if (!optionsOfQuestion.get(q.id()).contains(o.id())) optionsOfQuestion.get(q.id()).add(o.id());
                optionText.put(o.id(), o.text());
            }
        }
    }

    @Override public void setState(UUID pollId, PollState state) { states.put(pollId, state); }

    @Override public void addVote(UUID pollId, List<VoteAnswer> answers, int delta) {
        if (failOnVote) { rejectedByFailure++; throw new IllegalStateException("db down"); }
        states.putIfAbsent(pollId, PollState.ACTIVE);
        totals.merge(pollId, (long) delta, Long::sum);
        for (VoteAnswer a : answers) {
            questionsOfPoll.computeIfAbsent(pollId, k -> new ArrayList<>());
            if (!questionsOfPoll.get(pollId).contains(a.questionId())) questionsOfPoll.get(pollId).add(a.questionId());
            for (UUID o : a.optionIds()) {
                optionsOfQuestion.computeIfAbsent(a.questionId(), k -> new ArrayList<>());
                if (!optionsOfQuestion.get(a.questionId()).contains(o)) optionsOfQuestion.get(a.questionId()).add(o);
                votes.merge(o, (long) delta, Long::sum);
            }
        }
    }

    @Override public Optional<PollResult> find(UUID pollId) {
        if (!states.containsKey(pollId)) return Optional.empty();
        var questions = questionsOfPoll.getOrDefault(pollId, List.of()).stream().map(q -> new PollResult.QuestionResult(
                q, questionText.get(q), false,
                optionsOfQuestion.getOrDefault(q, List.of()).stream()
                        .map(o -> new PollResult.OptionResult(o, optionText.get(o), votes.getOrDefault(o, 0L))).toList())).toList();
        return Optional.of(new PollResult(pollId, titles.get(pollId), states.get(pollId).name(),
                totals.getOrDefault(pollId, 0L), questions));
    }
}
