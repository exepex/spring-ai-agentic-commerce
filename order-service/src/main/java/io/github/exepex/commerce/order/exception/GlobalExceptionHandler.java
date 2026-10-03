package io.github.exepex.commerce.order.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Answers every failed request of the order API as an RFC 9457 problem detail: the order service's own failures with
 * their status, message and extra properties, and invalid requests as Spring describes them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(OrderException.class)
    ResponseEntity<ProblemDetail> handleOrderException(OrderException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        exception.getProperties().forEach(problem::setProperty);
        return ResponseEntity.status(exception.getStatus()).body(problem);
    }
}
