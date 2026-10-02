package io.github.exepex.commerce.mcp.governance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    Optional<RefundRequest> findByIdempotencyKey(String idempotencyKey);

    List<RefundRequest> findByStatusOrderByCreatedAt(RefundRequest.Status status);

    List<RefundRequest> findTop100ByOrderByCreatedAtDesc();

    List<RefundRequest> findByOrderIdOrderByCreatedAt(UUID orderId);

    /** Moves the request from one status to another in one statement; 0 if it was no longer in {@code from}. */
    @Modifying
    @Transactional
    @Query("update RefundRequest r set r.status = :to where r.id = :id and r.status = :from")
    int moveStatus(@Param("id") UUID id, @Param("from") RefundRequest.Status from, @Param("to") RefundRequest.Status to);

    /**
     * Marks a refund the processor failed as failed, in one statement: one that was executed, or one still failed
     * because its success was never recorded. 0 if there is no such request, or it waits for or was refused approval.
     * The new version makes a payment-service answer still on its way fail to overwrite this.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefundRequest r set r.status = io.github.exepex.commerce.mcp.governance.RefundRequest.Status.FAILED,
                r.failure = :failure, r.updatedAt = :now, r.version = r.version + 1
            where r.idempotencyKey = :idempotencyKey
              and r.status in (io.github.exepex.commerce.mcp.governance.RefundRequest.Status.EXECUTED,
                               io.github.exepex.commerce.mcp.governance.RefundRequest.Status.FAILED)""")
    int failAtProcessor(@Param("idempotencyKey") String idempotencyKey, @Param("failure") String failure,
            @Param("now") Instant now);
}
