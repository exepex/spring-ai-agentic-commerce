package io.github.exepex.commerce.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Something the payment service was asked to do cannot be done; the status says how a caller should take it. */
@Getter
public abstract class PaymentException extends RuntimeException {

    private final HttpStatus status;

    protected PaymentException(HttpStatus status, String message) {
        this(status, message, null);
    }

    protected PaymentException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
