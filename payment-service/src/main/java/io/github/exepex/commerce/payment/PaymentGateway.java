package io.github.exepex.commerce.payment;

import java.math.BigDecimal;

/** The card processor. Every call carries an idempotency key, so a retried request never charges or refunds twice. */
interface PaymentGateway {

    record ChargeResult(boolean succeeded, String reference, String failureMessage) {

        static ChargeResult succeeded(String reference) {
            return new ChargeResult(true, reference, null);
        }

        static ChargeResult declined(String reference, String failureMessage) {
            return new ChargeResult(false, reference, failureMessage);
        }
    }

    String name();

    ChargeResult charge(BigDecimal amount, String currency, String paymentMethod, String description,
            String idempotencyKey);

    /** Returns the processor's reference for the refund. */
    String refund(String chargeReference, BigDecimal amount, String idempotencyKey);
}
