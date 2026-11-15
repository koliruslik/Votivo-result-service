package com.votivo.result;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResultServiceApplicationTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void emptyResultHasStableJsonContract() throws Exception {
        String poll = "11111111-1111-4111-8111-111111111111";
        mvc.perform(get("/api/v1/polls/" + poll + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pollId").value(poll))
                .andExpect(jsonPath("$.totalVotes").value(0));
    }

    @Test
    void invalidPollIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/polls/not-a-uuid/results"))
                .andExpect(status().isBadRequest());
    }
}
