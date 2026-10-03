package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The refund's reason is longer than the payment service stores; it is refused before any money moves. */
public class RefundReasonTooLongException extends CommerceException {

    public RefundReasonTooLongException(int maxLength) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.REFUND_REASON_TOO_LONG.formatted(maxLength));
    }
}
