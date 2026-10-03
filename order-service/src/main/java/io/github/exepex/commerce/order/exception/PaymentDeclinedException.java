package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The payment service declined the card; the message is the processor's reason, safe to show the customer. */
public class PaymentDeclinedException extends CommerceException {

    public PaymentDeclinedException(String reason) {
        super(HttpStatus.PAYMENT_REQUIRED, reason);
    }
}
