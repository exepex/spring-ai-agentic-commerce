package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** The payment's outcome is not known: it may or may not have been taken. */
public class PaymentUnavailableException extends OrderException {

    public PaymentUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorMessages.PAYMENT_SERVICE_UNAVAILABLE, cause);
    }
}
