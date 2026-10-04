package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.ConfigKeys;
import io.github.exepex.commerce.payment.constants.JobLocks;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
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

    private static final Limit BATCH = Limit.of(500);

    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Value(ConfigKeys.REFUND_CHECK_WATCH)
    private final Duration watch;

    /** One instance at a time; the oldest refunds first, a batch per run, so a backlog cannot swamp it. */
    @Scheduled(fixedDelayString = ConfigKeys.REFUND_CHECK_INTERVAL, initialDelayString = ConfigKeys.REFUND_CHECK_INTERVAL)
    @SchedulerLock(name = JobLocks.REFUND_CHECK)
    void reconcile() {
        for (var refund : refunds.findUnsettled(gateway.name(), Instant.now(clock).minus(watch), BATCH)) {
            try {
                var latest = gateway.refundStatus(refund.getProviderReference());
                if (latest != refund.getStatus()) {
                    settle(refund, latest);
                }
            } catch (RuntimeException failure) {
                log.warn("Could not check refund {} with the card processor; it will be retried", refund.getId(), failure);
            }
        }
    }

    /** Under the payment's lock, so it cannot interleave with a new refund's refundable check. */
    private void settle(Refund refund, PaymentGateway.RefundStatus latest) {
        transaction.executeWithoutResult(status -> {
            var payment = payments.findByIdForUpdate(refund.getPaymentId()).orElseThrow();
            var current = refunds.findById(refund.getId()).orElseThrow();
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
