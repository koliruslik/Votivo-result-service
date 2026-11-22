package com.votivo.result.messaging;

import java.util.List;
import java.util.UUID;

/** The bodies of the events Result Service consumes (JSON, snake_case). Unknown fields are ignored. */
final class Events {
    private Events() { }

    record PollStarted(UUID pollId, String title, List<Question> questions) {
        record Question(UUID id, String text, boolean multiple, List<Option> options) { }

        record Option(UUID id, String text) { }
    }

    record PollEnded(UUID pollId, String reason) { }

    record Vote(UUID pollId, List<Answer> answers) {
        record Answer(UUID questionId, List<UUID> optionIds) { }
    }
}
