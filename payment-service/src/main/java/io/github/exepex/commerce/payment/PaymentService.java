package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.PaymentValues;
import io.github.exepex.commerce.payment.exception.ChargeConflictException;
import io.github.exepex.commerce.payment.exception.IdempotencyKeyReusedException;
import io.github.exepex.commerce.payment.exception.PaymentNotFoundException;
import io.github.exepex.commerce.payment.exception.RefundExceedsPaymentException;
import io.github.exepex.commerce.payment.exception.RefundNotCompletedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentGateway gateway;
    private final Clock clock;

    /** Charges the order once. Asking again for the same order returns the first result instead of charging again. */
    public Payment charge(UUID orderId, String customerEmail, BigDecimal amount, String currency, String paymentMethod) {
        return payments.findByOrderId(orderId)
                .map(earlier -> {
                    if (!earlier.isSameChargeAs(customerEmail, amount, currency)) {
                        throw new ChargeConflictException(orderId);
                    }
                    return earlier;
                })
                .orElseGet(() -> {
                    var charge = gateway.charge(amount, currency, paymentMethod,
                            PaymentValues.CHARGE_DESCRIPTION.formatted(orderId),
                            PaymentValues.CHARGE_IDEMPOTENCY_KEY.formatted(orderId));
                    return payments.save(new Payment(orderId, customerEmail, amount, currency, gateway.name(), charge,
                            Instant.now(clock)));
                });
    }

    public Payment getPayment(UUID orderId) {
        return payments.findByOrderId(orderId).orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    public List<Refund> refundsOf(Payment payment) {
        return refunds.findByPaymentIdOrderByCreatedAt(payment.getId());
    }

    /**
     * Refunds part or all of the order's payment. The caller's idempotency key makes retries safe: repeating a
     * refund returns the first one, here and at the card processor. The payment row stays locked while the processor
     * is called, so concurrent refunds cannot together exceed what was paid. A pending refund counts as refunded
     * until {@link RefundReconciler} learns that it failed. Repeating a refund that failed reports the failure.
     */
    @Transactional
    public Refund refund(UUID orderId, BigDecimal amount, String reason, String idempotencyKey) {
        var payment = payments.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
        var earlier = refunds.findByIdempotencyKey(idempotencyKey);
        if (earlier.isPresent()) {
            return repeated(earlier.get(), payment, amount, idempotencyKey);
        }
        if (amount.compareTo(payment.refundable()) > 0) {
            throw new RefundExceedsPaymentException(amount, payment.refundable());
        }
        var result = gateway.refund(payment.getProviderReference(), amount, idempotencyKey);
        payment.recordRefund(amount);
        return refunds.save(new Refund(payment.getId(), amount, reason, idempotencyKey, result, Instant.now(clock)));
    }

    private static Refund repeated(Refund refund, Payment payment, BigDecimal amount, String idempotencyKey) {
        if (!refund.isSameRefundAs(payment.getId(), amount)) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        if (refund.getStatus() == PaymentGateway.RefundStatus.FAILED) {
            throw new RefundNotCompletedException(PaymentValues.STRIPE_FAILED);
        }
        return refund;
    }
}
