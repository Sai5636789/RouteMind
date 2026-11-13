package com.routemind.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.config.SecurityConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// The JWT filter is part of the slice through SecurityConfig; its
// JwtService is mocked so these tests verify the controller, not token
// parsing (JwtServiceTest covers that). DispatcherCredentials is mocked
// so no real password is involved.
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private DispatcherCredentials credentials;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginWithValidCredentialsReturnsToken() throws Exception {
        when(credentials.matches("dispatcher", "changeme")).thenReturn(true);
        when(jwtService.generateToken("dispatcher")).thenReturn("test-token");
        when(jwtService.tokenTtl()).thenReturn(Duration.ofHours(8));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dispatcher\",\"password\":\"changeme\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(28800))
                .andExpect(jsonPath("$.username").value("dispatcher"));
    }

    @Test
    void loginWithWrongPasswordReturns401WithoutLeakingWhichHalfWasWrong() throws Exception {
        when(credentials.matches(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dispatcher\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginWithMissingPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dispatcher\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointAcceptsBearerToken() throws Exception {
        // The filter authenticates from the header, so a request carrying
        // a token the JwtService accepts reaches the handler instead of
        // answering 401. There is no /vehicles handler in this slice, so
        // 404 — not 401 — is the expected outcome.
        when(jwtService.extractUsername("good-token")).thenReturn(java.util.Optional.of("dispatcher"));

        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer good-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedEndpointRejectsInvalidBearerToken() throws Exception {
        when(jwtService.extractUsername("bad-token")).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }
}
