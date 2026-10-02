package io.github.exepex.commerce.payment;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The payment-service's error responses, as RFC 9457 problem details. */
final class PaymentProblems {

    private PaymentProblems() {}

    static ErrorResponseException paymentNotFound(UUID orderId) {
        return problem(HttpStatus.NOT_FOUND, "Order " + orderId + " has no payment");
    }

    static ErrorResponseException refundExceedsPayment(BigDecimal requested, BigDecimal refundable) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT,
                "Refund of " + requested + " exceeds the " + refundable + " still refundable");
    }

    static ErrorResponseException idempotencyKeyReused(String idempotencyKey) {
        return problem(HttpStatus.CONFLICT,
                "Idempotency key " + idempotencyKey + " was already used for a different refund");
    }

    private static ErrorResponseException problem(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
