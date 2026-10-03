package io.github.exepex.commerce.order.exception;

import org.springframework.http.HttpStatus;

/** The payment service declined the card; the message is the processor's reason, safe to show the customer. */
public class PaymentDeclinedException extends OrderException {

    public PaymentDeclinedException(String reason) {
        super(HttpStatus.PAYMENT_REQUIRED, reason);
    }
}
