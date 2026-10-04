package io.github.exepex.commerce.platform.error;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A service that cannot get a database connection in time, because it is overloaded or its database is down, answers
 * 503 with a {@code Retry-After}, not 500: callers then treat it as a service that is briefly unavailable, as the
 * checkout and the agents' tools already do, instead of as a bug.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class DatabaseUnavailableExceptionHandler {

    static final String BUSY = "The service is busy or its database is unavailable; try again shortly";
    static final String RETRY_AFTER_SECONDS = "1";

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    ResponseEntity<ProblemDetail> handleDatabaseUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, BUSY));
    }
}
