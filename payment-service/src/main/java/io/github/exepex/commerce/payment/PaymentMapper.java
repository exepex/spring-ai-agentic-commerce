package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.dto.PaymentView;
import io.github.exepex.commerce.payment.dto.RefundView;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How payments and refunds are shown through the payment API. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PaymentMapper {

    static RefundView toView(Refund refund) {
        return new RefundView(refund.getId(), refund.getAmount(), refund.getReason(), refund.getIdempotencyKey(),
                refund.getProviderReference(), refund.getStatus().name(), refund.getCreatedAt());
    }

    static PaymentView toView(Payment payment, List<Refund> refunds) {
        return new PaymentView(payment.getId(), payment.getOrderId(), payment.getCustomerEmail(), payment.getAmount(),
                payment.getRefundedAmount(), payment.refundable(), payment.getCurrency(), payment.getStatus().name(),
                payment.getProvider(), payment.getProviderReference(), payment.getFailureMessage(),
                payment.getCreatedAt(), refunds.stream().map(PaymentMapper::toView).toList());
    }
}
