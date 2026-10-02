package io.github.exepex.commerce.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentGateway gateway;
    private final Clock clock;

    PaymentService(PaymentRepository payments, RefundRepository refunds, PaymentGateway gateway, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.gateway = gateway;
        this.clock = clock;
    }

    /** Charges the order once. Asking again for the same order returns the first result instead of charging again. */
    public Payment charge(UUID orderId, String customerEmail, BigDecimal amount, String currency, String paymentMethod) {
        Optional<Payment> existing = payments.findByOrderId(orderId);
        if (existing.isPresent()) {
            Payment earlier = existing.get();
            if (earlier.getAmount().compareTo(amount) != 0 || !earlier.getCurrency().equals(currency)
                    || !earlier.getCustomerEmail().equalsIgnoreCase(customerEmail)) {
                throw PaymentProblems.chargeConflicts(orderId);
            }
            return earlier;
        }
        PaymentGateway.ChargeResult charge = gateway.charge(amount, currency, paymentMethod,
                "Order " + orderId, "charge-" + orderId);
        return payments.save(new Payment(orderId, customerEmail, amount, currency, gateway.name(), charge,
                Instant.now(clock)));
    }

    public Payment getPayment(UUID orderId) {
        return payments.findByOrderId(orderId).orElseThrow(() -> PaymentProblems.paymentNotFound(orderId));
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
        Payment payment = payments.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> PaymentProblems.paymentNotFound(orderId));
        Optional<Refund> earlier = refunds.findByIdempotencyKey(idempotencyKey);
        if (earlier.isPresent()) {
            Refund refund = earlier.get();
            if (!refund.getPaymentId().equals(payment.getId()) || refund.getAmount().compareTo(amount) != 0) {
                throw PaymentProblems.idempotencyKeyReused(idempotencyKey);
            }
            if (refund.getStatus() == PaymentGateway.RefundStatus.FAILED) {
                throw PaymentProblems.refundNotCompleted("failed");
            }
            return refund;
        }
        if (amount.compareTo(payment.refundable()) > 0) {
            throw PaymentProblems.refundExceedsPayment(amount, payment.refundable());
        }
        PaymentGateway.RefundResult result = gateway.refund(payment.getProviderReference(), amount, idempotencyKey);
        payment.recordRefund(amount);
        return refunds.save(new Refund(payment.getId(), amount, reason, idempotencyKey, result, Instant.now(clock)));
    }
}
