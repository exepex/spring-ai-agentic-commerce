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

    /** Refunds that can still change: every pending one, and succeeded ones made after {@code succeededAfter}. */
    @Query("""
            select refund from Refund refund
            where refund.status = io.github.exepex.commerce.payment.PaymentGateway.RefundStatus.PENDING
               or (refund.status = io.github.exepex.commerce.payment.PaymentGateway.RefundStatus.SUCCEEDED
                   and refund.createdAt > :succeededAfter)""")
    List<Refund> findUnsettled(Instant succeededAfter);
}
