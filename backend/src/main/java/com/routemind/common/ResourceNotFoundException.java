package com.routemind.common;

/** A requested resource does not exist. Mapped to 404 by GlobalExceptionHandler. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
