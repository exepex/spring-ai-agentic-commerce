package io.github.exepex.commerce.payment;

import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import io.github.exepex.commerce.payment.constants.ErrorMessages;
import io.github.exepex.commerce.payment.constants.PaymentValues;
import io.github.exepex.commerce.payment.exception.PaymentProviderUnavailableException;
import io.github.exepex.commerce.payment.exception.RefundNotCompletedException;
import java.math.BigDecimal;
import java.util.Locale;

/** Charges and refunds through Stripe. Use a test-mode key: test cards such as {@code pm_card_visa} move no money. */
final class StripePaymentGateway implements PaymentGateway {

    private final StripeClient stripe;

    StripePaymentGateway(String secretKey) {
        this.stripe = new StripeClient(secretKey);
    }

    @Override
    public String name() {
        return PaymentValues.STRIPE;
    }

    @Override
    public ChargeResult charge(BigDecimal amount, String currency, String paymentMethod, String description,
            String idempotencyKey) {
        var params = PaymentIntentCreateParams.builder()
                .setAmount(StripeRequests.minorUnits(amount))
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .setPaymentMethod(paymentMethod)
                // Confirmed on the server, so no customer is present to follow a redirect: allow only methods without one.
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build())
                .setConfirm(true)
                .setDescription(description)
                .build();
        try {
            var intent = stripe.v1().paymentIntents().create(params, StripeRequests.idempotent(idempotencyKey));
            return PaymentValues.STRIPE_SUCCEEDED.equals(intent.getStatus())
                    ? ChargeResult.succeeded(intent.getId())
                    : ChargeResult.declined(intent.getId(), ErrorMessages.PAYMENT_ENDED_IN_STATUS.formatted(intent.getStatus()));
        } catch (CardException declined) {
            return ChargeResult.declined(StripeRequests.declinedPaymentIntent(declined), declined.getUserMessage());
        } catch (StripeException failure) {
            throw new PaymentProviderUnavailableException(failure);
        }
    }

    @Override
    public RefundResult refund(String chargeReference, BigDecimal amount, String idempotencyKey) {
        var params = RefundCreateParams.builder()
                .setPaymentIntent(chargeReference)
                .setAmount(StripeRequests.minorUnits(amount))
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .build();
        try {
            var refund = stripe.v1().refunds().create(params, StripeRequests.idempotent(idempotencyKey));
            var status = statusOf(refund.getStatus());
            if (status == RefundStatus.FAILED) {
                throw new RefundNotCompletedException(refund.getStatus());
            }
            return new RefundResult(refund.getId(), status);
        } catch (StripeException failure) {
            throw new PaymentProviderUnavailableException(failure);
        }
    }

    @Override
    public RefundStatus refundStatus(String refundReference) {
        try {
            return statusOf(stripe.v1().refunds().retrieve(refundReference).getStatus());
        } catch (StripeException failure) {
            throw new PaymentProviderUnavailableException(failure);
        }
    }

    /**
     * Stripe's refund statuses: a failed or cancelled refund returned no money; anything not yet final, such as
     * {@code pending} or {@code requires_action}, is pending.
     */
    static RefundStatus statusOf(String stripeStatus) {
        return switch (stripeStatus) {
            case PaymentValues.STRIPE_SUCCEEDED -> RefundStatus.SUCCEEDED;
            case PaymentValues.STRIPE_FAILED, PaymentValues.STRIPE_CANCELED -> RefundStatus.FAILED;
            default -> RefundStatus.PENDING;
        };
    }
}
