package io.github.exepex.commerce.mcp.governance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByOrderIdOrderByOccurredAt(UUID orderId);

    List<AuditEvent> findTop200ByOrderByOccurredAtDesc();

    boolean existsBySourceEventIdAndOrderId(UUID sourceEventId, UUID orderId);
}
