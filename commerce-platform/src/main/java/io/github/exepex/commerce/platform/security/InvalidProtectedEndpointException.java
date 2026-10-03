package io.github.exepex.commerce.platform.security;

/** A protected endpoint is not written as {@code METHOD /path}, so the service refuses to start unguarded. */
public class InvalidProtectedEndpointException extends RuntimeException {

    InvalidProtectedEndpointException(String endpoint) {
        super("A protected endpoint is written as 'METHOD /path', not '%s'".formatted(endpoint));
    }
}
