package io.github.exepex.commerce.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the payment API answers with, and how payments and refunds turn into it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PaymentViews {

    record OutageView(boolean active) {}

    record RefundView(UUID id, BigDecimal amount, String reason, String idempotencyKey, String providerReference,
            String status, Instant createdAt) {}

    record PaymentView(UUID id, UUID orderId, String customerEmail, BigDecimal amount, BigDecimal refundedAmount,
            BigDecimal refundable, String currency, String status, String provider, String providerReference,
            String failureMessage, Instant createdAt, List<RefundView> refunds) {}

    static RefundView toView(Refund refund) {
        return new RefundView(refund.getId(), refund.getAmount(), refund.getReason(), refund.getIdempotencyKey(),
                refund.getProviderReference(), refund.getStatus().name(), refund.getCreatedAt());
    }

    static PaymentView toView(Payment payment, List<Refund> refunds) {
        return new PaymentView(payment.getId(), payment.getOrderId(), payment.getCustomerEmail(), payment.getAmount(),
                payment.getRefundedAmount(), payment.refundable(), payment.getCurrency(), payment.getStatus().name(),
                payment.getProvider(), payment.getProviderReference(), payment.getFailureMessage(),
                payment.getCreatedAt(), refunds.stream().map(PaymentViews::toView).toList());
    }
}
