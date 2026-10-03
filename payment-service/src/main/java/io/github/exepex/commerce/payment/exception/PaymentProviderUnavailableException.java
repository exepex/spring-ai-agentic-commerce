package io.github.exepex.commerce.payment.exception;

import io.github.exepex.commerce.payment.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** The card processor did not answer, so whether it charged or refunded is not known yet. */
public class PaymentProviderUnavailableException extends PaymentException {

    public PaymentProviderUnavailableException(Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, ErrorMessages.PROVIDER_UNAVAILABLE, cause);
    }
}
