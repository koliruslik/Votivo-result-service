package com.votivo.result.messaging;

import com.votivo.result.domain.PollSnapshot;
import com.votivo.result.domain.PollState;
import com.votivo.result.domain.VoteAnswer;
import com.votivo.result.service.ResultService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResultEventListenerTest {
    private final ResultService results = mock(ResultService.class);
    private final ResultEventListener listener = new ResultEventListener(results);
    private final UUID eventId = UUID.randomUUID();
    private final UUID poll = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final UUID question = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private final UUID option = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private Message message(String messageId, String routingKey, String body) {
        var properties = new MessageProperties();
        properties.setMessageId(messageId);
        properties.setReceivedRoutingKey(routingKey);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private Message message(String routingKey, String body) {
        return message(eventId.toString(), routingKey, body);
    }

    @Test void pollStartedBecomesASnapshot() {
        listener.onPollEvent(message("poll.started", """
                {"poll_id":"%s","title":"Lunch","anonymous":true,"allow_vote_change":false,"extra":1,
                 "questions":[{"id":"%s","text":"Where?","multiple":true,"options":[{"id":"%s","text":"Pizza"}]}]}
                """.formatted(poll, question, option)));

        var captured = ArgumentCaptor.forClass(PollSnapshot.class);
        verify(results).pollStarted(eq(eventId), captured.capture());
        assertThat(captured.getValue()).isEqualTo(new PollSnapshot(poll, "Lunch", List.of(
                new PollSnapshot.Question(question, "Where?", true, List.of(new PollSnapshot.Option(option, "Pizza"))))));
    }

    @Test void pollEndedMapsTheReason() {
        listener.onPollEvent(message("poll.ended", "{\"poll_id\":\"%s\",\"reason\":\"closed\"}".formatted(poll)));
        listener.onPollEvent(message("poll.ended", "{\"poll_id\":\"%s\",\"reason\":\"blocked\"}".formatted(poll)));

        verify(results).pollEnded(eventId, poll, PollState.CLOSED);
        verify(results).pollEnded(eventId, poll, PollState.BLOCKED);
    }

    @Test void voteEventsBecomeAnswers() {
        String body = "{\"poll_id\":\"%s\",\"request_id\":\"r\",\"answers\":[{\"question_id\":\"%s\",\"option_ids\":[\"%s\"]}]}"
                .formatted(poll, question, option);

        listener.onVoteEvent(message("vote.cast", body));
        listener.onVoteEvent(message("vote.revoked", body));

        List<VoteAnswer> answers = List.of(new VoteAnswer(question, List.of(option)));
        verify(results).voteCast(eventId, poll, answers);
        verify(results).voteRevoked(eventId, poll, answers);
    }

    @Test void messagesNoRetryCanFixAreRejectedWithoutRequeue() {
        String vote = "{\"poll_id\":\"%s\",\"answers\":[]}".formatted(poll);
        assertRejected(() -> listener.onVoteEvent(message("vote.cast", "not json")));
        assertRejected(() -> listener.onVoteEvent(message("vote.cast", "{}")));
        assertRejected(() -> listener.onVoteEvent(message("vote.cast", "{\"poll_id\":\"%s\"}".formatted(poll))));
        assertRejected(() -> listener.onVoteEvent(message("vote.cast", "{\"poll_id\":\"x\",\"answers\":[]}")));
        assertRejected(() -> listener.onVoteEvent(message("vote.deleted", vote)));
        assertRejected(() -> listener.onVoteEvent(message(null, "vote.cast", vote)));
        assertRejected(() -> listener.onVoteEvent(message("not-a-uuid", "vote.cast", vote)));
        assertRejected(() -> listener.onPollEvent(message("poll.ended", "{\"poll_id\":\"%s\",\"reason\":\"x\"}".formatted(poll))));
        assertRejected(() -> listener.onPollEvent(message("poll.started", "{\"poll_id\":\"%s\"}".formatted(poll))));
        assertRejected(() -> listener.onPollEvent(message("poll.archived", "{}")));

        verifyNoInteractions(results);
    }

    @Test void aStorageFailureIsNotRejectedSoTheContainerRetriesIt() {
        doThrow(new IllegalStateException("db down")).when(results).voteCast(any(), any(), any());

        assertThatThrownBy(() -> listener.onVoteEvent(message("vote.cast",
                "{\"poll_id\":\"%s\",\"answers\":[]}".formatted(poll))))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static void assertRejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
