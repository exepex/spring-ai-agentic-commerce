package io.github.exepex.commerce.payment;

import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import java.math.BigDecimal;
import java.util.Locale;

/** Charges and refunds through Stripe. Use a test-mode key: test cards such as {@code pm_card_visa} move no money. */
class StripePaymentGateway implements PaymentGateway {

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
                .setAmount(minorUnits(amount))
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
            PaymentIntent intent = stripe.v1().paymentIntents().create(params, idempotent(idempotencyKey));
            return "succeeded".equals(intent.getStatus())
                    ? ChargeResult.succeeded(intent.getId())
                    : ChargeResult.declined(intent.getId(), "Payment ended in status " + intent.getStatus());
        } catch (CardException declined) {
            String reference = declined.getStripeError() != null && declined.getStripeError().getPaymentIntent() != null
                    ? declined.getStripeError().getPaymentIntent().getId()
                    : null;
            return ChargeResult.declined(reference, declined.getUserMessage());
        } catch (StripeException failure) {
            throw new PaymentProviderUnavailableException(failure);
        }
    }

    @Override
    public String refund(String chargeReference, BigDecimal amount, String idempotencyKey) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(chargeReference)
                .setAmount(minorUnits(amount))
                .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                .build();
        try {
            Refund refund = stripe.v1().refunds().create(params, idempotent(idempotencyKey));
            ensureAccepted(refund.getStatus());
            return refund.getId();
        } catch (StripeException failure) {
            throw new PaymentProviderUnavailableException(failure);
        }
    }

    /**
     * A refund Stripe reports as failed or cancelled did not return any money, so it must not count as refunded. A
     * pending one has been accepted and completes on Stripe's side.
     */
    static void ensureAccepted(String refundStatus) {
        if ("failed".equals(refundStatus) || "canceled".equals(refundStatus)) {
            throw PaymentProblems.refundNotCompleted(refundStatus);
        }
    }

    private static long minorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private static RequestOptions idempotent(String idempotencyKey) {
        return RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
    }
}
