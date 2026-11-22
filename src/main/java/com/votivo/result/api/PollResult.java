package com.votivo.result.api;

import java.util.List;
import java.util.UUID;

/**
 * The result of a poll. {@code status} is {@code ACTIVE}, {@code CLOSED} or {@code BLOCKED} (the count is final),
 * or {@code UNKNOWN} when no event about the poll has arrived yet; then {@code title} is null and there are no questions.
 * {@code totalVotes} counts ballots, not options: in a multiple-choice question the options add up to more.
 */
public record PollResult(UUID pollId, String title, String status, long totalVotes, List<QuestionResult> questions) {
    public record QuestionResult(UUID questionId, String text, boolean multiple, List<OptionResult> options) { }

    public record OptionResult(UUID optionId, String text, long votes) { }

    public static PollResult unknown(UUID pollId) {
        return new PollResult(pollId, null, "UNKNOWN", 0, List.of());
    }
}
