package io.github.exepex.commerce.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.RateLimitException;
import io.github.exepex.commerce.payment.constants.ErrorMessages;
import io.github.exepex.commerce.payment.exception.PaymentProviderUnavailableException;
import org.junit.jupiter.api.Test;

class StripeChargeFailureTest {

    @Test
    void aDeclinedCardDeclinesThePayment() {
        var declined = new CardException("Your card was declined.", "req_1", "card_declined", null, "generic_decline",
                null, 402, null);

        var result = StripePaymentGateway.chargeFailure(declined);

        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void aPaymentMethodStripeDoesNotKnowDeclinesThePaymentInsteadOfLeavingItPendingForever() {
        var rejected = new InvalidRequestException("No such PaymentMethod: 'pm_garbage'", "payment_method", "req_1",
                "resource_missing", 400, null);

        var result = StripePaymentGateway.chargeFailure(rejected);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failureMessage()).isEqualTo(ErrorMessages.PAYMENT_REQUEST_REJECTED);
    }

    @Test
    void aFailureThatMayPassLeavesTheOutcomeUnknown() {
        assertThatThrownBy(() -> StripePaymentGateway.chargeFailure(new ApiConnectionException("timed out")))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        assertThatThrownBy(() -> StripePaymentGateway.chargeFailure(
                new RateLimitException("Too many requests", null, "req_1", "rate_limit", 429, null)))
                .isInstanceOf(PaymentProviderUnavailableException.class);
        assertThatThrownBy(() -> StripePaymentGateway.chargeFailure(
                new ApiException("Stripe had a problem", "req_1", "api_error", 500, null)))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }
}
