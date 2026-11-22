package com.votivo.result.domain;

import java.util.List;
import java.util.UUID;

/** The structure of a poll as published by Poll Service: what the results are counted for. */
public record PollSnapshot(UUID pollId, String title, List<Question> questions) {
    public record Question(UUID id, String text, boolean multiple, List<Option> options) { }

    public record Option(UUID id, String text) { }
}
