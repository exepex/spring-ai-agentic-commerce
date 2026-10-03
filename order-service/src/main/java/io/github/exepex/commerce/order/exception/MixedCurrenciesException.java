package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The products asked for are priced in more than one currency; an order is paid in one. */
public class MixedCurrenciesException extends CommerceException {

    public MixedCurrenciesException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.MIXED_CURRENCIES);
    }
}
