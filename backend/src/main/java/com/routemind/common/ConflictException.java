package com.routemind.common;

/**
 * The request conflicts with the current state — a duplicate unique value,
 * or an operation the resource's state does not allow. Mapped to 409 by
 * GlobalExceptionHandler.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
