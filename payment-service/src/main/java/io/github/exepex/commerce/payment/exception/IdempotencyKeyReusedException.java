package io.github.exepex.commerce.payment.exception;

import io.github.exepex.commerce.payment.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The idempotency key already names another refund, so this one cannot be a retry of it. */
public class IdempotencyKeyReusedException extends CommerceException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super(HttpStatus.CONFLICT, ErrorMessages.IDEMPOTENCY_KEY_REUSED.formatted(idempotencyKey));
    }
}
