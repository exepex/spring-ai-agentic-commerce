package io.github.exepex.commerce.mcp.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Answers every failed request of the governance and case API as an RFC 9457 problem detail: a rule that stopped it,
 * or a commerce service that refused it, with its status, message and extra properties, and invalid requests as
 * Spring describes them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GovernanceException.class)
    ResponseEntity<ProblemDetail> handleGovernanceException(GovernanceException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        exception.getProperties().forEach(problem::setProperty);
        return ResponseEntity.status(exception.getStatus()).body(problem);
    }
}
