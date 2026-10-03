package io.github.exepex.commerce.order.exception;

import io.github.exepex.commerce.order.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** The products asked for are priced in more than one currency; an order is paid in one. */
public class MixedCurrenciesException extends OrderException {

    public MixedCurrenciesException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.MIXED_CURRENCIES);
    }
}
