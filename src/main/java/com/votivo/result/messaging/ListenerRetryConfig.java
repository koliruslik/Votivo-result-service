package com.votivo.result.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

/**
 * Retries a failing message only when a retry can help. A transient failure (the database is down) is retried
 * five times with growing pauses and then rejected without requeue, so the queue dead-letters it. A message that is
 * rejected on purpose ({@link AmqpRejectAndDontRequeueException}: no event id, a body that does not decode) is
 * dead-lettered at once instead of holding up the queue while it is retried in vain.
 */
@Configuration
class ListenerRetryConfig {
    @Bean
    ContainerCustomizer<SimpleMessageListenerContainer> retryOnlyTransientFailures() {
        return container -> container.setAdviceChain(RetryInterceptorBuilder.stateless()
                .configureRetryPolicy(policy -> policy
                        .maxRetries(4)
                        .delay(Duration.ofSeconds(1))
                        .multiplier(2)
                        .maxDelay(Duration.ofSeconds(10))
                        .excludes(AmqpRejectAndDontRequeueException.class))
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
    }
}
