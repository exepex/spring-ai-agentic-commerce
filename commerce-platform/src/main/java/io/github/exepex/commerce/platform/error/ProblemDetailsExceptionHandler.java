package io.github.exepex.commerce.platform.error;

import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The one place a service's failures become HTTP answers: its own {@link CommerceException}s with their status,
 * message and properties, and invalid requests as Spring describes them. It takes the place of Spring Boot's own
 * problem-details handler, in the services that switch problem details on.
 */
@Order(0)
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CommerceException.class)
    ResponseEntity<ProblemDetail> handleCommerceException(CommerceException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        exception.getProperties().forEach(problem::setProperty);
        return ResponseEntity.status(exception.getStatus()).body(problem);
    }
}
