package io.github.exepex.commerce.payment.exception;

import io.github.exepex.commerce.payment.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The order was never charged. */
public class PaymentNotFoundException extends PaymentException {

    public PaymentNotFoundException(UUID orderId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.PAYMENT_NOT_FOUND.formatted(orderId));
    }
}
