package io.github.exepex.commerce.mcp.governance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EscalationRepository extends JpaRepository<Escalation, UUID> {

    List<Escalation> findByStatusOrderByCreatedAt(Escalation.Status status);

    List<Escalation> findTop100ByOrderByCreatedAtDesc();
}
