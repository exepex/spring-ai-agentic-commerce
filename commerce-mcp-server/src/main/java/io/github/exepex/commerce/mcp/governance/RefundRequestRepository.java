package io.github.exepex.commerce.mcp.governance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    Optional<RefundRequest> findByIdempotencyKey(String idempotencyKey);

    List<RefundRequest> findByStatusOrderByCreatedAt(RefundRequest.Status status);

    List<RefundRequest> findTop100ByOrderByCreatedAtDesc();

    List<RefundRequest> findByOrderIdOrderByCreatedAt(UUID orderId);
}
