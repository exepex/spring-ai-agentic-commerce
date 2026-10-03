package io.github.exepex.commerce.payment;

import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
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
        return "stripe";
    }

    @Override
    public ChargeResult charge(BigDecimal amount, String currency, String paymentMethod, String description,
            String idempotencyKey) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
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
            PaymentIntent intent = stripe.v1().paymentIntents().create(params, StripeRequests.idempotent(idempotencyKey));
            return "succeeded".equals(intent.getStatus())
                    ? ChargeResult.succeeded(intent.getId())
                    : ChargeResult.declined(intent.getId(), "Payment ended in status " + intent.getStatus());
        } catch (CardException declined) {
            return ChargeResult.declined(StripeRequests.declinedPaymentIntent(declined), declined.getUserMessage());
        } catch (StripeException failure) {
            throw new PaymentProviderUnavailableException(failure);
        }
    }

    @Override
    public RefundResult refund(String chargeReference, BigDecimal amount, String idempotencyKey) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(chargeReference)
                .setAmount(StripeRequests.minorUnits(amount))
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .build();
        try {
            Refund refund = stripe.v1().refunds().create(params, StripeRequests.idempotent(idempotencyKey));
            RefundStatus status = statusOf(refund.getStatus());
            if (status == RefundStatus.FAILED) {
                throw PaymentProblems.refundNotCompleted(refund.getStatus());
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
            case "succeeded" -> RefundStatus.SUCCEEDED;
            case "failed", "canceled" -> RefundStatus.FAILED;
            default -> RefundStatus.PENDING;
        };
    }
}
