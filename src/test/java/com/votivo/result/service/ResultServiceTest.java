package com.votivo.result.service;

import com.votivo.result.api.PollResult;
import com.votivo.result.domain.PollSnapshot;
import com.votivo.result.domain.PollState;
import com.votivo.result.domain.VoteAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ResultServiceTest {
    private final UUID poll = UUID.randomUUID();
    private final UUID question = UUID.randomUUID();
    private final UUID pizza = UUID.randomUUID();
    private final UUID sushi = UUID.randomUUID();
    private InMemoryResultRepository repository;
    private ResultService service;

    @BeforeEach void setUp() {
        repository = new InMemoryResultRepository();
        service = new ResultService(repository);
    }

    private PollSnapshot snapshot() {
        return new PollSnapshot(poll, "Lunch", List.of(new PollSnapshot.Question(question, "Where?", false,
                List.of(new PollSnapshot.Option(pizza, "Pizza"), new PollSnapshot.Option(sushi, "Sushi")))));
    }

    private List<VoteAnswer> answer(UUID... options) {
        return List.of(new VoteAnswer(question, List.of(options)));
    }

    private long votesFor(UUID option) {
        return service.getResults(poll).questions().getFirst().options().stream()
                .filter(o -> o.optionId().equals(option)).findFirst().orElseThrow().votes();
    }

    @Test void aPollNobodyHeardOfIsUnknownAndEmpty() {
        PollResult result = service.getResults(poll);

        assertThat(result.status()).isEqualTo("UNKNOWN");
        assertThat(result.totalVotes()).isZero();
        assertThat(result.questions()).isEmpty();
    }

    @Test void aStartedPollListsEveryOptionWithZeroVotes() {
        service.pollStarted(UUID.randomUUID(), snapshot());

        PollResult result = service.getResults(poll);

        assertThat(result.title()).isEqualTo("Lunch");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.questions().getFirst().options()).extracting(PollResult.OptionResult::text, PollResult.OptionResult::votes)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Pizza", 0L), org.assertj.core.groups.Tuple.tuple("Sushi", 0L));
    }

    @Test void castAddsOneToEveryChosenOptionAndOneBallot() {
        service.pollStarted(UUID.randomUUID(), snapshot());

        service.voteCast(UUID.randomUUID(), poll, answer(pizza));
        service.voteCast(UUID.randomUUID(), poll, answer(pizza));
        service.voteCast(UUID.randomUUID(), poll, answer(sushi));

        assertThat(service.getResults(poll).totalVotes()).isEqualTo(3);
        assertThat(votesFor(pizza)).isEqualTo(2);
        assertThat(votesFor(sushi)).isEqualTo(1);
    }

    @Test void revokeSubtractsTheOldAnswers() {
        service.pollStarted(UUID.randomUUID(), snapshot());
        service.voteCast(UUID.randomUUID(), poll, answer(pizza));

        service.voteRevoked(UUID.randomUUID(), poll, answer(pizza));

        assertThat(service.getResults(poll).totalVotes()).isZero();
        assertThat(votesFor(pizza)).isZero();
    }

    @Test void changingAVoteIsARevokeAndACast() {
        service.pollStarted(UUID.randomUUID(), snapshot());
        service.voteCast(UUID.randomUUID(), poll, answer(pizza));

        service.voteRevoked(UUID.randomUUID(), poll, answer(pizza));
        service.voteCast(UUID.randomUUID(), poll, answer(sushi));

        assertThat(service.getResults(poll).totalVotes()).isEqualTo(1);
        assertThat(votesFor(pizza)).isZero();
        assertThat(votesFor(sushi)).isEqualTo(1);
    }

    @Test void aMessageDeliveredTwiceIsCountedOnce() {
        service.pollStarted(UUID.randomUUID(), snapshot());
        UUID eventId = UUID.randomUUID();

        service.voteCast(eventId, poll, answer(pizza));
        service.voteCast(eventId, poll, answer(pizza));

        assertThat(service.getResults(poll).totalVotes()).isEqualTo(1);
        assertThat(votesFor(pizza)).isEqualTo(1);
    }

    @Test void aMultipleChoiceVoteCountsEveryOptionButOneBallot() {
        service.pollStarted(UUID.randomUUID(), snapshot());

        service.voteCast(UUID.randomUUID(), poll, answer(pizza, sushi));

        assertThat(service.getResults(poll).totalVotes()).isEqualTo(1);
        assertThat(votesFor(pizza)).isEqualTo(1);
        assertThat(votesFor(sushi)).isEqualTo(1);
    }

    @Test void aVoteThatOvertookPollStartedIsKeptAndTheStructureFillsIn() {
        service.voteCast(UUID.randomUUID(), poll, answer(pizza));

        service.pollStarted(UUID.randomUUID(), snapshot());

        PollResult result = service.getResults(poll);
        assertThat(result.title()).isEqualTo("Lunch");
        assertThat(result.totalVotes()).isEqualTo(1);
        assertThat(votesFor(pizza)).isEqualTo(1);
        assertThat(votesFor(sushi)).isZero();
    }

    @Test void endingMarksTheResultFinalAndASecondStartDoesNotReopenIt() {
        service.pollStarted(UUID.randomUUID(), snapshot());
        service.pollEnded(UUID.randomUUID(), poll, PollState.CLOSED);

        service.pollStarted(UUID.randomUUID(), snapshot());

        assertThat(service.getResults(poll).status()).isEqualTo("CLOSED");
    }

    @Test void blockedIsReportedAsBlocked() {
        service.pollStarted(UUID.randomUUID(), snapshot());

        service.pollEnded(UUID.randomUUID(), poll, PollState.BLOCKED);

        assertThat(service.getResults(poll).status()).isEqualTo("BLOCKED");
    }
}
