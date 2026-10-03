package io.github.exepex.commerce.payment.exception;

import io.github.exepex.commerce.payment.constants.ErrorMessages;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

/** The refund asks for more than is left of what the customer paid. */
public class RefundExceedsPaymentException extends PaymentException {

    public RefundExceedsPaymentException(BigDecimal requested, BigDecimal refundable) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.REFUND_EXCEEDS_PAYMENT.formatted(requested, refundable));
    }
}
