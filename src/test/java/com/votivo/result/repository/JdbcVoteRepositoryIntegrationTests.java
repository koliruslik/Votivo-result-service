package com.votivo.result.repository;

import com.votivo.result.domain.VoteSubmission;
import com.votivo.result.domain.VoteSubmission.Answer;
import com.votivo.result.service.ResultService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "RESULTS_DB_PASSWORD=overridden-by-testcontainers")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class JdbcVoteRepositoryIntegrationTests {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.3-alpine");

    @Autowired
    private VoteRepository repository;

    @Autowired
    private ResultService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CommandLineRunner seedDemoVotes;

    @BeforeEach
    void clearProjection() {
        jdbc.execute("TRUNCATE result_vote CASCADE");
    }

    @Test
    void demoInitializationCanRunAgainWithoutDuplicatingPersistedVotes() throws Exception {
        seedDemoVotes.run();
        seedDemoVotes.run();
        UUID poll = UUID.fromString("11111111-1111-4111-8111-111111111111");
        var result = service.getResults(poll);
        assertThat(result.totalVotes()).isEqualTo(3);
        assertThat(result.questions().getFirst().options())
                .allSatisfy(option -> {
                    assertThat(option.votes()).isEqualTo(2);
                    assertThat(option.percentage()).isEqualTo(66.7);
                });
    }

    @Test
    void migratesFreshDatabaseAndReadsPersistedSelectionsFromAnotherAdapter() throws Exception {
        UUID poll = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        UUID option = UUID.randomUUID();
        var vote = new VoteSubmission(UUID.randomUUID(), poll,
                List.of(new Answer(question, Set.of(option, UUID.randomUUID()))));
        repository.save(vote);

        assertThat(new JdbcVoteRepository(jdbc).findByPollId(poll)).containsExactly(vote);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE",
                Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/polls/" + poll + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVotes").value(1))
                .andExpect(jsonPath("$.questions[0].answeredVotes").value(1))
                .andExpect(jsonPath("$.questions[0].options.length()").value(2))
                .andExpect(jsonPath("$.questions[0].options[0].percentage").value(100.0));
    }

    @Test
    void concurrentDuplicateDeliveryIsCountedOnce() throws Exception {
        UUID poll = UUID.randomUUID();
        var vote = new VoteSubmission(UUID.randomUUID(), poll,
                List.of(new Answer(UUID.randomUUID(), Set.of(UUID.randomUUID()))));
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<Void>> deliveries = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> (Callable<Void>) () -> {
                        repository.save(vote);
                        return null;
                    }).toList();
            for (var future : executor.invokeAll(deliveries)) {
                future.get();
            }
        }
        assertThat(service.getResults(poll).totalVotes()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM result_selection", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void rollsBackAllRowsWhenASelectionCannotBeStored() {
        UUID forbidden = UUID.randomUUID();
        // A temporary DB constraint simulates a failed child-row insert after the parent insert.
        jdbc.execute("ALTER TABLE result_selection ADD CONSTRAINT test_reject_option CHECK "
                + "(option_id <> '" + forbidden + "'::uuid)");
        UUID poll = UUID.randomUUID();
        var vote = new VoteSubmission(UUID.randomUUID(), poll,
                List.of(new Answer(UUID.randomUUID(), Set.of(forbidden))));
        try {
            assertThatThrownBy(() -> repository.save(vote))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM result_vote", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM result_answer", Integer.class)).isZero();
            assertThat(repository.findByPollId(poll)).isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE result_selection DROP CONSTRAINT test_reject_option");
        }
    }

    @Test
    void isolatesPollsAndCountsMultipleChoiceAgainstAnsweredSubmissions() {
        UUID poll = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.save(new VoteSubmission(UUID.randomUUID(), poll,
                List.of(new Answer(question, Set.of(first, second)))));
        repository.save(new VoteSubmission(UUID.randomUUID(), poll,
                List.of(new Answer(question, Set.of(first)))));
        repository.save(new VoteSubmission(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new Answer(question, Set.of(second)))));

        var result = service.getResults(poll);
        assertThat(result.totalVotes()).isEqualTo(2);
        assertThat(result.questions().getFirst().options()).anySatisfy(option -> {
            assertThat(option.optionId()).isEqualTo(second);
            assertThat(option.votes()).isEqualTo(1);
            assertThat(option.percentage()).isEqualTo(50.0);
        });
        assertThat(repository.findByPollId(UUID.randomUUID())).isEmpty();
    }
}
