package com.votivo.result.api;

import java.util.UUID;

public record PollResult(UUID pollId, long totalVotes) {}
