package io.github.exepex.commerce.mcp.governance;

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
}
