package com.routemind.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.auth.JwtService;
import com.routemind.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    // SecurityConfig builds its JWT filter from JwtService; the mock
    // keeps the slice focused on the controller, not on token parsing.
    @MockitoBean
    private JwtService jwtService;

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
    void readEndpointsRequireAuthenticationNow() throws Exception {
        // The temporary public reads ended with JWT login: this slice only
        // contains HealthController, so there is no handler for /vehicles
        // here — the point is only that security answers 401 instead of
        // letting the GET through.
        mockMvc.perform(get("/api/v1/vehicles")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginEndpointIsPublic() throws Exception {
        // No handler in this slice, so the login POST is not expected to
        // succeed — only that security does not answer 401 for it.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dispatcher\",\"password\":\"changeme\"}"))
                .andExpect(result -> {
                    if (result.getResponse().getStatus() == 401) {
                        throw new AssertionError("POST /api/v1/auth/login must not require authentication");
                    }
                });
    }
}
