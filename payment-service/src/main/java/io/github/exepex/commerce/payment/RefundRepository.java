package io.github.exepex.commerce.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    List<Refund> findByPaymentIdOrderByCreatedAt(UUID paymentId);

    /**
     * Refunds of {@code provider}'s payments that can still change: every pending one, and succeeded ones made after
     * {@code succeededAfter}. Only the processor that made a refund knows it.
     */
    @Query("""
            select refund from Refund refund, Payment payment
            where refund.paymentId = payment.id and payment.provider = :provider
              and (refund.status = io.github.exepex.commerce.payment.PaymentGateway.RefundStatus.PENDING
                   or (refund.status = io.github.exepex.commerce.payment.PaymentGateway.RefundStatus.SUCCEEDED
                       and refund.createdAt > :succeededAfter))""")
    List<Refund> findUnsettled(String provider, Instant succeededAfter);
}
