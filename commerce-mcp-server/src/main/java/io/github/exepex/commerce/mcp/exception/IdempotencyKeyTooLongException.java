package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The idempotency key is longer than the database stores. */
public class IdempotencyKeyTooLongException extends CommerceException {

    public IdempotencyKeyTooLongException(int maxLength) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.IDEMPOTENCY_KEY_TOO_LONG.formatted(maxLength));
    }
}
