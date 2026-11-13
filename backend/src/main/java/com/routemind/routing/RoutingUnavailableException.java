package com.routemind.routing;

/**
 * The road router is disabled, unreachable, or returned something the
 * client cannot use. Mapped to HTTP 503 by {@code GlobalExceptionHandler}:
 * routing is a dependency, not a client error, and the fleet map falls
 * back to straight lines when it happens.
 */
public class RoutingUnavailableException extends RuntimeException {

    public RoutingUnavailableException(String message) {
        super(message);
    }

    public RoutingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
