package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** A service this one depends on did not answer; the request can be retried. */
public class DependencyUnavailableException extends CommerceException {

    public DependencyUnavailableException(String dependency, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorMessages.DEPENDENCY_UNAVAILABLE.formatted(dependency), cause);
    }
}
