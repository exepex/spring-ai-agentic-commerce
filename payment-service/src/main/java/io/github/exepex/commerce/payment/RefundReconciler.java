package io.github.exepex.commerce.payment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asks the card processor again about refunds that can still change: pending ones until they settle, and succeeded
 * ones for {@code commerce.payments.refund-check.watch} after they succeeded, because a succeeded refund can still
 * fail. A failed refund returned no money, so its amount is taken off the payment's refunded amount, and a
 * {@link RefundFailedEvent} tells the governance service, which hands the order to a person. Only refunds of
 * payments the current processor took are checked: after switching between the simulator and Stripe, the other one's
 * refunds are unknown to it. The demo asks instead of receiving Stripe webhooks, so it needs no public URL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RefundReconciler {

    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Value("${commerce.payments.refund-check.watch}")
    private final Duration watch;

    @Scheduled(fixedDelayString = "${commerce.payments.refund-check.interval}",
            initialDelayString = "${commerce.payments.refund-check.interval}")
    void reconcile() {
        for (Refund refund : refunds.findUnsettled(gateway.name(), Instant.now(clock).minus(watch))) {
            try {
                PaymentGateway.RefundStatus latest = gateway.refundStatus(refund.getProviderReference());
                if (latest != refund.getStatus()) {
                    record(refund, latest);
                }
            } catch (RuntimeException failure) {
                log.warn("Could not check refund {} with the card processor; it will be retried", refund.getId(), failure);
            }
        }
    }

    /** Under the payment's lock, so it cannot interleave with a new refund's refundable check. */
    private void record(Refund refund, PaymentGateway.RefundStatus latest) {
        transaction.executeWithoutResult(status -> {
            Payment payment = payments.findByIdForUpdate(refund.getPaymentId()).orElseThrow();
            Refund current = refunds.findById(refund.getId()).orElseThrow();
            if (current.getStatus() == PaymentGateway.RefundStatus.FAILED) {
                return;
            }
            if (latest == PaymentGateway.RefundStatus.FAILED) {
                payment.reverseRefund(current.getAmount());
                events.publishEvent(RefundFailedEvent.of(payment, current, Instant.now(clock)));
                log.warn("Refund {} of {} {} failed at the card processor; no money was returned",
                        current.getId(), current.getAmount(), payment.getCurrency());
            }
            current.updateStatus(latest, Instant.now(clock));
        });
    }
}
