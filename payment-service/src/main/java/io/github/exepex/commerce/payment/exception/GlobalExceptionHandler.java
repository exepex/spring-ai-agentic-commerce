package io.github.exepex.commerce.payment.exception;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Answers every failed request of the payment API as an RFC 9457 problem detail: the payment service's own failures
 * with their status and message, and invalid requests as Spring describes them.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    ResponseEntity<ProblemDetail> handlePaymentException(PaymentException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(problem);
    }
}
