package com.votivo.result.api;

import java.util.List;
import java.util.UUID;

public record PollResult(UUID pollId, long totalVotes, List<QuestionResult> questions) {
    public record QuestionResult(UUID questionId, long answeredVotes, List<OptionResult> options) {}

    public record OptionResult(UUID optionId, long votes, double percentage) {}
}
