package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** A service this one depends on did not answer; the request can be retried. */
public class DependencyUnavailableException extends OrderException {

    public DependencyUnavailableException(String dependency, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorMessages.DEPENDENCY_UNAVAILABLE.formatted(dependency), cause);
    }
}
