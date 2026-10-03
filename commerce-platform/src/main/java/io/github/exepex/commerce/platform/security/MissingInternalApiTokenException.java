package io.github.exepex.commerce.platform.security;

/** A service guards or calls internal endpoints without a token, so it refuses to start rather than run unguarded. */
public class MissingInternalApiTokenException extends RuntimeException {

    MissingInternalApiTokenException() {
        super("commerce.internal-api.token must be set when internal endpoints are protected or called");
    }
}
