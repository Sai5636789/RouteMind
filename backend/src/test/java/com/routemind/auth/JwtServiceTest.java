package com.routemind.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "a-dev-only-test-secret-that-is-long-enough-0123456789";

    private JwtService service(Duration ttl) {
        return new JwtService(new AuthProperties(SECRET, ttl, "dispatcher", "changeme"));
    }

    @Test
    void generatedTokenRoundTripsToItsUsername() {
        JwtService jwt = service(Duration.ofHours(8));

        String token = jwt.generateToken("dispatcher");

        assertThat(jwt.extractUsername(token)).contains("dispatcher");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = service(Duration.ofHours(8));
        String foreignToken = other.generateToken("dispatcher");

        JwtService jwt = new JwtService(new AuthProperties(
                "a-different-test-secret-that-is-long-enough-0123456789",
                Duration.ofHours(8), "dispatcher", "changeme"));

        assertThat(jwt.extractUsername(foreignToken)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = service(Duration.ofHours(8));
        String token = jwt.generateToken("dispatcher");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwt.extractUsername(tampered)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        JwtService jwt = service(Duration.ofMillis(50));
        String token = jwt.generateToken("dispatcher");
        Thread.sleep(100);

        assertThat(jwt.extractUsername(token)).isEmpty();
    }

    @Test
    void blankTokenIsRejected() {
        assertThat(service(Duration.ofHours(8)).extractUsername(" ")).isEmpty();
        assertThat(service(Duration.ofHours(8)).extractUsername(null)).isEmpty();
    }

    @Test
    void shortSecretRefusesToStart() {
        assertThatThrownBy(() -> new JwtService(new AuthProperties(
                        "too-short", Duration.ofHours(8), "dispatcher", "changeme")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }
}
