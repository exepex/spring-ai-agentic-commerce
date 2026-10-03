package io.github.exepex.commerce.payment.exception;

import io.github.exepex.commerce.payment.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The card processor did not return the money: the refund failed or was cancelled. */
public class RefundNotCompletedException extends CommerceException {

    public RefundNotCompletedException(String providerStatus) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.REFUND_NOT_COMPLETED.formatted(providerStatus));
    }
}
