package io.github.exepex.commerce.payment.exception;

import io.github.exepex.commerce.payment.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The order was charged before, for another customer, amount or currency than asked now. */
public class ChargeConflictException extends PaymentException {

    public ChargeConflictException(UUID orderId) {
        super(HttpStatus.CONFLICT, ErrorMessages.CHARGE_CONFLICTS.formatted(orderId));
    }
}
