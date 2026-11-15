package com.votivo.result.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record VoteSubmission(UUID voteId, UUID pollId, List<Answer> answers) {
    public VoteSubmission {
        Objects.requireNonNull(voteId);
        Objects.requireNonNull(pollId);
        answers = List.copyOf(answers);
        if (answers.isEmpty()) {
            throw new IllegalArgumentException("A vote must contain at least one answer");
        }
        if (answers.stream().map(Answer::questionId).distinct().count() != answers.size()) {
            throw new IllegalArgumentException("A vote cannot answer the same question twice");
        }
    }

    public record Answer(UUID questionId, Set<UUID> optionIds) {
        public Answer {
            Objects.requireNonNull(questionId);
            optionIds = Set.copyOf(optionIds);
            if (optionIds.isEmpty()) {
                throw new IllegalArgumentException("An answer must select at least one option");
            }
        }
    }
}
