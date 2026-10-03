package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.ErrorMessages;
import io.github.exepex.commerce.payment.constants.PaymentValues;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for Stripe when no key is configured, so the demo and the tests run without an account. It follows
 * Stripe's test-card conventions: {@code pm_card_chargeDeclined} is declined, and a refund of a charge made with
 * {@code pm_card_refundFail} succeeds at first and is reported failed from the second time it is asked about. Every
 * other card succeeds.
 */
final class SimulatedPaymentGateway implements PaymentGateway {

    private final Map<String, String> referencesByIdempotencyKey = new ConcurrentHashMap<>();
    private final Set<String> failingRefundReferences = ConcurrentHashMap.newKeySet();
    private final Set<String> chargesWhoseRefundsFail = ConcurrentHashMap.newKeySet();
    private final Set<String> checkedFailingRefunds = ConcurrentHashMap.newKeySet();

    @Override
    public String name() {
        return PaymentValues.SIMULATED;
    }

    @Override
    public ChargeResult charge(BigDecimal amount, String currency, String paymentMethod, String description,
            String idempotencyKey) {
        var reference = referencesByIdempotencyKey.computeIfAbsent(idempotencyKey,
                key -> PaymentValues.SIMULATED_CHARGE_PREFIX + UUID.randomUUID());
        if (PaymentValues.REFUND_FAILS_CARD.equals(paymentMethod)) {
            chargesWhoseRefundsFail.add(reference);
        }
        return PaymentValues.DECLINED_CARD.equals(paymentMethod)
                ? ChargeResult.declined(reference, ErrorMessages.CARD_DECLINED)
                : ChargeResult.succeeded(reference);
    }

    @Override
    public RefundResult refund(String chargeReference, BigDecimal amount, String idempotencyKey) {
        var reference = referencesByIdempotencyKey.computeIfAbsent(idempotencyKey,
                key -> PaymentValues.SIMULATED_REFUND_PREFIX + UUID.randomUUID());
        if (chargesWhoseRefundsFail.contains(chargeReference)) {
            failingRefundReferences.add(reference);
        }
        return new RefundResult(reference, RefundStatus.SUCCEEDED);
    }

    @Override
    public RefundStatus refundStatus(String refundReference) {
        if (!failingRefundReferences.contains(refundReference)) {
            return RefundStatus.SUCCEEDED;
        }
        return checkedFailingRefunds.add(refundReference) ? RefundStatus.SUCCEEDED : RefundStatus.FAILED;
    }
}
