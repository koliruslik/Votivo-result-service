package com.votivo.result.domain;

import java.util.UUID;

public record VoteSubmission(UUID voteId, UUID pollId) {}
