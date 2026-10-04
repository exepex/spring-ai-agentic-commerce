package io.github.exepex.commerce.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    List<Refund> findByPaymentIdOrderByCreatedAt(UUID paymentId);

    /**
     * Refunds of {@code provider}'s payments that can still change: every pending one, and ones that succeeded after
     * {@code succeededAfter}. Only the processor that made a refund knows it. The least recently checked come first,
     * so refunds that stay pending cannot keep the others from being checked.
     */
    @Query("""
            select refund from Refund refund, Payment payment
            where refund.paymentId = payment.id and payment.provider = :provider
              and (refund.status = io.github.exepex.commerce.payment.PaymentGateway.RefundStatus.PENDING
                   or (refund.status = io.github.exepex.commerce.payment.PaymentGateway.RefundStatus.SUCCEEDED
                       and refund.succeededAt > :succeededAfter))
            order by refund.checkedAt asc nulls first, refund.createdAt""")
    List<Refund> findUnsettled(String provider, Instant succeededAfter, Limit limit);

    @Modifying
    @Transactional
    @Query("update Refund refund set refund.checkedAt = :checkedAt where refund.id = :id")
    void markChecked(@Param("id") UUID id, @Param("checkedAt") Instant checkedAt);
}
