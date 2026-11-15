package com.votivo.result.service;

import com.votivo.result.domain.VoteSubmission;
import com.votivo.result.domain.VoteSubmission.Answer;
import com.votivo.result.repository.InMemoryVoteRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResultServiceTests {
    private final InMemoryVoteRepository repository = new InMemoryVoteRepository();
    private final ResultService service = new ResultService(repository);

    @Test
    void countsMultipleSelectionsAgainstAnsweredSubmissionsAndIsolatesPolls() {
        UUID poll = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        UUID optional = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.save(new VoteSubmission(UUID.randomUUID(), poll, List.of(
                new Answer(question, Set.of(first, second)),
                new Answer(optional, Set.of(first)))));
        repository.save(new VoteSubmission(UUID.randomUUID(), poll,
                List.of(new Answer(question, Set.of(first)))));
        repository.save(new VoteSubmission(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new Answer(question, Set.of(second)))));

        var result = service.getResults(poll);
        assertThat(result.totalVotes()).isEqualTo(2);
        var main = result.questions().stream()
                .filter(item -> item.questionId().equals(question)).findFirst().orElseThrow();
        assertThat(main.answeredVotes()).isEqualTo(2);
        assertThat(main.options()).anySatisfy(option -> {
            assertThat(option.optionId()).isEqualTo(first);
            assertThat(option.votes()).isEqualTo(2);
            assertThat(option.percentage()).isEqualTo(100.0);
        }).anySatisfy(option -> {
            assertThat(option.optionId()).isEqualTo(second);
            assertThat(option.votes()).isEqualTo(1);
            assertThat(option.percentage()).isEqualTo(50.0);
        });
        var skipped = result.questions().stream()
                .filter(item -> item.questionId().equals(optional)).findFirst().orElseThrow();
        assertThat(skipped.answeredVotes()).isEqualTo(1);
        assertThat(skipped.options().getFirst().percentage()).isEqualTo(100.0);
    }

    @Test
    void duplicateVoteDeliveryDoesNotIncreaseTotals() {
        UUID poll = UUID.randomUUID();
        var vote = new VoteSubmission(UUID.randomUUID(), poll, List.of(
                new Answer(UUID.randomUUID(), Set.of(UUID.randomUUID()))));
        repository.save(vote);
        repository.save(vote);
        assertThat(service.getResults(poll).totalVotes()).isEqualTo(1);
    }

    @Test
    void emptyPollHasNoVotesOrQuestions() {
        var result = service.getResults(UUID.randomUUID());
        assertThat(result.totalVotes()).isZero();
        assertThat(result.questions()).isEmpty();
    }

    @Test
    void rejectsRepeatedQuestionInOneSubmission() {
        var answer = new Answer(UUID.randomUUID(), Set.of(UUID.randomUUID()));
        assertThatThrownBy(() -> new VoteSubmission(
                UUID.randomUUID(), UUID.randomUUID(), List.of(answer, answer)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
