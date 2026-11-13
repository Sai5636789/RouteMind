package com.routemind.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The single seeded dispatcher account.
 *
 * The password arrives as plain text from configuration (environment
 * variable in practice) and is BCrypt-hashed once, in memory, at startup —
 * the plain text is never stored, logged, or returned. There is no user
 * table and no password-change endpoint: rotate the account by changing
 * DISPATCHER_USERNAME / DISPATCHER_PASSWORD and restarting.
 */
@Component
public class DispatcherCredentials {

    private static final Logger log = LoggerFactory.getLogger(DispatcherCredentials.class);

    private final String username;
    private final String passwordHash;
    private final PasswordEncoder passwordEncoder;

    public DispatcherCredentials(AuthProperties properties, PasswordEncoder passwordEncoder) {
        this.username = properties.dispatcherUsername();
        // A fresh salt each boot: the hash is only ever compared with
        // matches(), never persisted, so re-hashing on startup is fine.
        this.passwordHash = passwordEncoder.encode(properties.dispatcherPassword());
        this.passwordEncoder = passwordEncoder;
        if (properties.usesDevPassword()) {
            log.warn("Using the default development dispatcher password — set DISPATCHER_PASSWORD before any shared deployment");
        }
    }

    /**
     * Whether the given username and password are the dispatcher's.
     * The username comparison is constant-time so a wrong username does
     * not answer measurably faster than a wrong password.
     */
    public boolean matches(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            return false;
        }
        boolean usernameMatches = MessageDigest.isEqual(
                username.getBytes(StandardCharsets.UTF_8),
                this.username.getBytes(StandardCharsets.UTF_8));
        return usernameMatches && passwordEncoder.matches(rawPassword, passwordHash);
    }

    public String username() {
        return username;
    }
}
