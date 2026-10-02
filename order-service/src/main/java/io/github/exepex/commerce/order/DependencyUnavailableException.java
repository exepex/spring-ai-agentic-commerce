package io.github.exepex.commerce.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A service this one depends on did not answer; the request can be retried. */
class DependencyUnavailableException extends ErrorResponseException {

    DependencyUnavailableException(String dependency, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The " + dependency + " is unavailable; try again"),
                cause);
    }
}
