package com.votivo.result.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The exchanges and queues Result Service reads. It declares everything it needs, so the start order of the services
 * does not matter. A message that cannot be processed is dead-lettered to {@code votivo.dlx} under the name of its queue
 * and kept in the queue named {@code <queue>.dlq}.
 */
@Configuration
public class EventTopology {
    public static final String POLL_EXCHANGE = "poll.events.exchange";
    public static final String VOTE_EXCHANGE = "vote.events.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "votivo.dlx";

    public static final String POLL_EVENTS_QUEUE = "result-service.poll-events";
    public static final String VOTE_EVENTS_QUEUE = "result-service.vote-events";

    public static final String POLL_STARTED = "poll.started";
    public static final String POLL_ENDED = "poll.ended";
    public static final String VOTE_CAST = "vote.cast";
    public static final String VOTE_REVOKED = "vote.revoked";

    @Bean TopicExchange pollEventsExchange() { return new TopicExchange(POLL_EXCHANGE, true, false); }
    @Bean TopicExchange voteEventsExchange() { return new TopicExchange(VOTE_EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false); }

    @Bean Queue pollEventsQueue() { return queue(POLL_EVENTS_QUEUE); }
    @Bean Queue voteEventsQueue() { return queue(VOTE_EVENTS_QUEUE); }
    @Bean Queue pollEventsDeadLetterQueue() { return QueueBuilder.durable(POLL_EVENTS_QUEUE + ".dlq").build(); }
    @Bean Queue voteEventsDeadLetterQueue() { return QueueBuilder.durable(VOTE_EVENTS_QUEUE + ".dlq").build(); }

    @Bean Binding pollStartedBinding() { return bind(pollEventsQueue(), pollEventsExchange(), POLL_STARTED); }
    @Bean Binding pollEndedBinding() { return bind(pollEventsQueue(), pollEventsExchange(), POLL_ENDED); }
    @Bean Binding voteCastBinding() { return bind(voteEventsQueue(), voteEventsExchange(), VOTE_CAST); }
    @Bean Binding voteRevokedBinding() { return bind(voteEventsQueue(), voteEventsExchange(), VOTE_REVOKED); }
    @Bean Binding pollEventsDeadLetterBinding() {
        return BindingBuilder.bind(pollEventsDeadLetterQueue()).to(deadLetterExchange()).with(POLL_EVENTS_QUEUE);
    }
    @Bean Binding voteEventsDeadLetterBinding() {
        return BindingBuilder.bind(voteEventsDeadLetterQueue()).to(deadLetterExchange()).with(VOTE_EVENTS_QUEUE);
    }

    private static Queue queue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(name)
                .build();
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
