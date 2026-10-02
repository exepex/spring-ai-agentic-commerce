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

    /** Where a refund stands at the processor. Only a failed refund returned no money. */
    enum RefundStatus {
        PENDING,
        SUCCEEDED,
        FAILED
    }

    record RefundResult(String reference, RefundStatus status) {}

    String name();

    ChargeResult charge(BigDecimal amount, String currency, String paymentMethod, String description,
            String idempotencyKey);

    /** Refunds part of a charge. A refund the processor rejects outright is not returned: it throws instead. */
    RefundResult refund(String chargeReference, BigDecimal amount, String idempotencyKey);

    /** Asks the processor again: a pending or even a succeeded refund can still fail later. */
    RefundStatus refundStatus(String refundReference);
}
