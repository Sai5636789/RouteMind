package com.routemind.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.routemind.config.SecurityConfig;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublicAndReportsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("routemind-api"));
    }

    @Test
    void writesStillRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\":\"ABC-123\",\"model\":\"Van\",\"capacityKg\":500}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readEndpointsAreTemporarilyPublic() throws Exception {
        // TEMPORARY: reads are public for the local demo until JWT login
        // lands. This slice only contains HealthController, so there is no
        // handler for /vehicles here — the point is only that security lets
        // the GET through instead of answering 401.
        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(result -> {
                    if (result.getResponse().getStatus() == 401) {
                        throw new AssertionError("GET /api/v1/vehicles must not require authentication yet");
                    }
                });
    }
}
