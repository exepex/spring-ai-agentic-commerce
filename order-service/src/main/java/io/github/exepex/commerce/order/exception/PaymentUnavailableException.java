package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The payment's outcome is not known: it may or may not have been taken. */
public class PaymentUnavailableException extends CommerceException {

    public PaymentUnavailableException(Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorMessages.PAYMENT_SERVICE_UNAVAILABLE, cause);
    }
}
