package com.routemind.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication settings (bound from {@code routemind.auth} in
 * application.yml; every value comes from the environment in practice).
 *
 * There is exactly one account: the seeded dispatcher. No user table, no
 * roles, no registration — that was never requested, and a single
 * dispatcher is the whole account model for this dashboard.
 */
@ConfigurationProperties("routemind.auth")
public record AuthProperties(
        /**
         * HMAC signing secret for JWTs. At least 32 characters; the
         * application refuses to start with a shorter one. The default is a
         * local-development value only — override it with JWT_SECRET
         * everywhere else, because anyone holding it can forge tokens.
         */
        String jwtSecret,

        /** How long a login token stays valid. */
        Duration tokenTtl,

        /** Username of the seeded dispatcher account. */
        String dispatcherUsername,

        /**
         * Password of the seeded dispatcher account, in plain text as
         * configured. It is BCrypt-hashed in memory at startup by
         * {@link DispatcherCredentials} and never stored or logged.
         * The default ({@code changeme}) is a local-development value only —
         * override it with DISPATCHER_PASSWORD.
         */
        String dispatcherPassword) {

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException("routemind.auth.jwt-secret must be set");
        }
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("routemind.auth.token-ttl must be a positive duration");
        }
        if (dispatcherUsername == null || dispatcherUsername.isBlank()) {
            throw new IllegalArgumentException("routemind.auth.dispatcher-username must be set");
        }
        if (dispatcherPassword == null || dispatcherPassword.isBlank()) {
            throw new IllegalArgumentException("routemind.auth.dispatcher-password must be set");
        }
    }

    /** The local-development secret; using it is safe only on a local machine. */
    public static final String DEV_JWT_SECRET =
            "routemind-dev-secret-change-me-before-any-shared-deployment-0123456789";

    /** The local-development dispatcher password. */
    public static final String DEV_DISPATCHER_PASSWORD = "changeme";

    public boolean usesDevSecret() {
        return DEV_JWT_SECRET.equals(jwtSecret);
    }

    public boolean usesDevPassword() {
        return DEV_DISPATCHER_PASSWORD.equals(dispatcherPassword);
    }
}
