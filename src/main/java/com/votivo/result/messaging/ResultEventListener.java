package com.votivo.result.messaging;

import com.votivo.result.domain.PollSnapshot;
import com.votivo.result.domain.PollState;
import com.votivo.result.domain.VoteAnswer;
import com.votivo.result.service.ResultService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Reads the poll and vote events. A message that no retry can fix (no event id, a body that does not decode,
 * an unknown routing key or reason) is rejected without requeue and ends in the dead-letter queue. Any other
 * failure, such as a database outage, propagates: the container retries the message and dead-letters it only
 * when the retries run out.
 */
@Component
public class ResultEventListener {
    private static final JsonMapper JSON = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ResultService results;

    public ResultEventListener(ResultService results) {
        this.results = results;
    }

    @RabbitListener(queues = EventTopology.POLL_EVENTS_QUEUE)
    public void onPollEvent(Message message) {
        UUID eventId = eventId(message);
        String key = routingKey(message);
        switch (key) {
            case EventTopology.POLL_STARTED -> {
                Events.PollStarted event = read(message, Events.PollStarted.class);
                results.pollStarted(eventId, snapshot(event));
            }
            case EventTopology.POLL_ENDED -> {
                Events.PollEnded event = read(message, Events.PollEnded.class);
                results.pollEnded(eventId, require(event.pollId()), state(event.reason()));
            }
            default -> throw reject("unexpected routing key " + key);
        }
    }

    @RabbitListener(queues = EventTopology.VOTE_EVENTS_QUEUE)
    public void onVoteEvent(Message message) {
        UUID eventId = eventId(message);
        String key = routingKey(message);
        switch (key) {
            case EventTopology.VOTE_CAST -> {
                Events.Vote event = read(message, Events.Vote.class);
                results.voteCast(eventId, require(event.pollId()), answers(event));
            }
            case EventTopology.VOTE_REVOKED -> {
                Events.Vote event = read(message, Events.Vote.class);
                results.voteRevoked(eventId, require(event.pollId()), answers(event));
            }
            default -> throw reject("unexpected routing key " + key);
        }
    }

    private static PollSnapshot snapshot(Events.PollStarted event) {
        if (event.questions() == null) throw reject("poll.started without questions");
        return new PollSnapshot(require(event.pollId()), event.title(), event.questions().stream()
                .map(q -> new PollSnapshot.Question(require(q.id()), q.text(), q.multiple(),
                        q.options() == null ? List.<PollSnapshot.Option>of() : q.options().stream()
                                .map(o -> new PollSnapshot.Option(require(o.id()), o.text())).toList()))
                .toList());
    }

    private static List<VoteAnswer> answers(Events.Vote event) {
        if (event.answers() == null) throw reject("vote event without answers");
        return event.answers().stream().map(a -> {
            if (a.optionIds() == null) throw reject("answer without option_ids");
            return new VoteAnswer(require(a.questionId()), a.optionIds().stream().map(ResultEventListener::require).toList());
        }).toList();
    }

    private static PollState state(String reason) {
        if ("closed".equals(reason)) return PollState.CLOSED;
        if ("blocked".equals(reason)) return PollState.BLOCKED;
        throw reject("poll.ended with unknown reason " + reason);
    }

    /** The AMQP message_id is the event id; a message without a valid one cannot be applied exactly once. */
    private static UUID eventId(Message message) {
        String id = message.getMessageProperties().getMessageId();
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw reject("message_id is not a UUID: " + id);
        }
    }

    private static String routingKey(Message message) {
        String key = message.getMessageProperties().getReceivedRoutingKey();
        return key == null ? "" : key;
    }

    private static <T> T read(Message message, Class<T> type) {
        try {
            T event = JSON.readValue(new String(message.getBody(), StandardCharsets.UTF_8), type);
            if (event == null) throw reject("empty body");
            return event;
        } catch (JacksonException e) {
            throw reject("body does not decode: " + e.getOriginalMessage());
        }
    }

    private static UUID require(UUID id) {
        if (id == null) throw reject("a required id is missing");
        return id;
    }

    private static AmqpRejectAndDontRequeueException reject(String reason) {
        return new AmqpRejectAndDontRequeueException(reason);
    }
}
