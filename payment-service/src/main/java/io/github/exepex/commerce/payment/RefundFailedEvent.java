package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.PaymentValues;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published on the {@code payment.events} topic, keyed by order id, when the card processor reports a refund failed
 * after it was recorded: the customer did not get that money back.
 */
public record RefundFailedEvent(UUID eventId, String type, UUID orderId, String idempotencyKey, BigDecimal amount,
        String currency, Instant occurredAt) {

    static RefundFailedEvent of(Payment payment, Refund refund, Instant now) {
        return new RefundFailedEvent(UUID.randomUUID(), PaymentValues.REFUND_FAILED_EVENT, payment.getOrderId(), refund.getIdempotencyKey(),
                refund.getAmount(), payment.getCurrency(), now);
    }
}
