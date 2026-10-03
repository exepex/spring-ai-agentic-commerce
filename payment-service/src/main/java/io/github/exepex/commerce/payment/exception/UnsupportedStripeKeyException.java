package io.github.exepex.commerce.payment.exception;

import io.github.exepex.commerce.payment.constants.ErrorMessages;

/** The configured Stripe key is not a test-mode key; the demo refuses to start rather than move real money. */
public class UnsupportedStripeKeyException extends RuntimeException {

    public UnsupportedStripeKeyException() {
        super(ErrorMessages.STRIPE_TEST_KEY_REQUIRED);
    }
}
