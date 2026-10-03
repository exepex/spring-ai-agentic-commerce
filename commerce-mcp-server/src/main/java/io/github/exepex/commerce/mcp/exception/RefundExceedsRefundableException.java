package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

/** The refund asks for more than is still refundable on the order. */
public class RefundExceedsRefundableException extends CommerceException {

    public RefundExceedsRefundableException(BigDecimal amount, String currency, BigDecimal refundable) {
        super(HttpStatus.UNPROCESSABLE_CONTENT,
                ErrorMessages.REFUND_EXCEEDS_REFUNDABLE.formatted(amount, currency, refundable));
    }
}
