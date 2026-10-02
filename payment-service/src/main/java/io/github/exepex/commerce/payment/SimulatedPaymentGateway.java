package io.github.exepex.commerce.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for Stripe when no key is configured, so the demo and the tests run without an account. It follows
 * Stripe's test-card conventions: {@code pm_card_chargeDeclined} is declined, and a refund of a charge made with
 * {@code pm_card_refundFail} first succeeds and then fails when asked about again. Every other card succeeds.
 */
class SimulatedPaymentGateway implements PaymentGateway {

    static final String DECLINED_CARD = "pm_card_chargeDeclined";
    static final String REFUND_FAILS_CARD = "pm_card_refundFail";

    private final Map<String, String> referencesByIdempotencyKey = new ConcurrentHashMap<>();
    private final Set<String> failingRefundReferences = ConcurrentHashMap.newKeySet();
    private final Set<String> chargesWhoseRefundsFail = ConcurrentHashMap.newKeySet();

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public ChargeResult charge(BigDecimal amount, String currency, String paymentMethod, String description,
            String idempotencyKey) {
        String reference = referencesByIdempotencyKey.computeIfAbsent(idempotencyKey, key -> "sim_pi_" + UUID.randomUUID());
        if (REFUND_FAILS_CARD.equals(paymentMethod)) {
            chargesWhoseRefundsFail.add(reference);
        }
        return DECLINED_CARD.equals(paymentMethod)
                ? ChargeResult.declined(reference, "Your card was declined.")
                : ChargeResult.succeeded(reference);
    }

    @Override
    public RefundResult refund(String chargeReference, BigDecimal amount, String idempotencyKey) {
        String reference = referencesByIdempotencyKey.computeIfAbsent(idempotencyKey, key -> "sim_re_" + UUID.randomUUID());
        if (chargesWhoseRefundsFail.contains(chargeReference)) {
            failingRefundReferences.add(reference);
        }
        return new RefundResult(reference, RefundStatus.SUCCEEDED);
    }

    @Override
    public RefundStatus refundStatus(String refundReference) {
        return failingRefundReferences.contains(refundReference) ? RefundStatus.FAILED : RefundStatus.SUCCEEDED;
    }
}
