package io.github.exepex.commerce.payment;

import com.stripe.exception.CardException;
import com.stripe.net.RequestOptions;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How amounts, retries and declines are expressed in Stripe's terms. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StripeRequests {

    /** Stripe counts money in cents. */
    static long minorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    /** A retried request with the same key is answered with the first result instead of charging or refunding again. */
    static RequestOptions idempotent(String idempotencyKey) {
        return RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
    }

    /** The payment a declined card left behind, when Stripe created one. */
    static String declinedPaymentIntent(CardException declined) {
        return declined.getStripeError() instanceof com.stripe.model.StripeError error
                && error.getPaymentIntent() != null ? error.getPaymentIntent().getId() : null;
    }
}
