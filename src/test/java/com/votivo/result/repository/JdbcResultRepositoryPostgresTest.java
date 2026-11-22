package com.votivo.result.repository;

import com.votivo.result.api.PollResult;
import com.votivo.result.domain.PollSnapshot;
import com.votivo.result.domain.PollState;
import com.votivo.result.domain.VoteAnswer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the SQL on a real PostgreSQL (TEST_DB_URL...): the ON CONFLICT upserts are PostgreSQL-specific. */
@Tag("postgres")
@SpringBootTest
@ActiveProfiles("postgres-test")
class JdbcResultRepositoryPostgresTest {
    @Autowired ResultRepository repository;
    @Autowired JdbcClient jdbc;

    private final UUID poll = UUID.randomUUID();
    private final UUID question = UUID.randomUUID();
    private final UUID pizza = UUID.randomUUID();
    private final UUID sushi = UUID.randomUUID();

    @BeforeEach void clean() {
        jdbc.sql("DELETE FROM poll_results").update();
        jdbc.sql("DELETE FROM processed_events").update();
    }

    private PollSnapshot snapshot() {
        return new PollSnapshot(poll, "Lunch", List.of(new PollSnapshot.Question(question, "Where?", false,
                List.of(new PollSnapshot.Option(pizza, "Pizza"), new PollSnapshot.Option(sushi, "Sushi")))));
    }

    private List<VoteAnswer> answer(UUID option) {
        return List.of(new VoteAnswer(question, List.of(option)));
    }

    @Test void processedEventIsRecordedOnce() {
        UUID id = UUID.randomUUID();

        assertThat(repository.markProcessed(id)).isTrue();
        assertThat(repository.markProcessed(id)).isFalse();
    }

    @Test void countersMoveAndKeepTheStructureOrder() {
        repository.saveStructure(snapshot());
        repository.addVote(poll, answer(pizza), 1);
        repository.addVote(poll, answer(pizza), 1);
        repository.addVote(poll, answer(sushi), 1);
        repository.addVote(poll, answer(pizza), -1);

        PollResult result = repository.find(poll).orElseThrow();

        assertThat(result.totalVotes()).isEqualTo(2);
        assertThat(result.questions().getFirst().options())
                .extracting(PollResult.OptionResult::text, PollResult.OptionResult::votes)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Pizza", 1L), org.assertj.core.groups.Tuple.tuple("Sushi", 1L));
    }

    @Test void aVoteBeforeTheStructureIsKeptWhenTheStructureArrives() {
        repository.addVote(poll, answer(pizza), 1);
        assertThat(repository.find(poll).orElseThrow().title()).isNull();

        repository.saveStructure(snapshot());

        PollResult result = repository.find(poll).orElseThrow();
        assertThat(result.title()).isEqualTo("Lunch");
        assertThat(result.totalVotes()).isEqualTo(1);
        assertThat(result.questions().getFirst().text()).isEqualTo("Where?");
        assertThat(result.questions().getFirst().options().getFirst().votes()).isEqualTo(1);
    }

    @Test void theStateSurvivesTheStructure() {
        repository.setState(poll, PollState.BLOCKED);

        repository.saveStructure(snapshot());

        assertThat(repository.find(poll).orElseThrow().status()).isEqualTo("BLOCKED");
    }

    @Test void concurrentVotesAreNotLost() throws Exception {
        repository.saveStructure(snapshot());
        var pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 200; i++) pool.submit(() -> repository.addVote(poll, answer(pizza), 1));
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        PollResult result = repository.find(poll).orElseThrow();

        assertThat(result.totalVotes()).isEqualTo(200);
        assertThat(result.questions().getFirst().options().getFirst().votes()).isEqualTo(200);
    }
}
