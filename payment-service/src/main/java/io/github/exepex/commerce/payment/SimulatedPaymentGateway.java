package io.github.exepex.commerce.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for Stripe when no key is configured, so the demo and the tests run without an account. It follows
 * Stripe's test-card conventions: {@code pm_card_chargeDeclined} is declined, every other card succeeds.
 */
class SimulatedPaymentGateway implements PaymentGateway {

    static final String DECLINED_CARD = "pm_card_chargeDeclined";

    private final Map<String, String> referencesByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "simulated";
    }

    @Override
    public ChargeResult charge(BigDecimal amount, String currency, String paymentMethod, String description,
            String idempotencyKey) {
        String reference = referencesByIdempotencyKey.computeIfAbsent(idempotencyKey, key -> "sim_pi_" + UUID.randomUUID());
        return DECLINED_CARD.equals(paymentMethod)
                ? ChargeResult.declined(reference, "Your card was declined.")
                : ChargeResult.succeeded(reference);
    }

    @Override
    public String refund(String chargeReference, BigDecimal amount, String idempotencyKey) {
        return referencesByIdempotencyKey.computeIfAbsent(idempotencyKey, key -> "sim_re_" + UUID.randomUUID());
    }
}
