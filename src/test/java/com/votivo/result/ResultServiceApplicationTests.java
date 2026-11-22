package com.votivo.result;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResultServiceApplicationTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void resultOfAPollNoEventWasSeenForHasAStableJsonContract() throws Exception {
        String poll = "11111111-1111-4111-8111-111111111111";
        mvc.perform(get("/api/v1/polls/" + poll + "/results").header("X-Internal-Auth", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pollId").value(poll))
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.totalVotes").value(0))
                .andExpect(jsonPath("$.questions").isEmpty());
    }

    @Test
    void invalidPollIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/polls/not-a-uuid/results").header("X-Internal-Auth", "test-secret"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesCallsThatDidNotComeThroughTheGateway() throws Exception {
        String url = "/api/v1/polls/11111111-1111-4111-8111-111111111111/results";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("X-Internal-Auth", "guess")).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("X-Internal-Auth", "test-secret-and-more")).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("X-Internal-Auth", "")).andExpect(status().isUnauthorized());
    }

    @Test
    void readinessChecksTheDatabase() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
