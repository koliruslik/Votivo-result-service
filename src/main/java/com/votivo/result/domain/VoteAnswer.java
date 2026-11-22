package com.votivo.result.domain;

import java.util.List;
import java.util.UUID;

/** The options one vote chose for one question. */
public record VoteAnswer(UUID questionId, List<UUID> optionIds) { }
