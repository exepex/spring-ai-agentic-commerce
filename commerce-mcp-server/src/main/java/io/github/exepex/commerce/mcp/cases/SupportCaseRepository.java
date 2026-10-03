package io.github.exepex.commerce.mcp.cases;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SupportCaseRepository extends JpaRepository<SupportCase, UUID> {

    Optional<SupportCase> findByOrderIdAndTypeAndStatusNot(UUID orderId, CaseType type, SupportCase.Status status);

    List<SupportCase> findByOrderIdAndStatusIn(UUID orderId, Collection<SupportCase.Status> statuses);

    List<SupportCase> findByStatusInOrderByCreatedAt(Collection<SupportCase.Status> statuses);

    List<SupportCase> findByIdIn(Collection<UUID> ids);

    List<SupportCase> findTop100ByOrderByCreatedAtDesc();

    List<SupportCase> findByOrderIdOrderByCreatedAt(UUID orderId);

    Optional<SupportCase> findByTypeAndIncidentUrl(CaseType type, String incidentUrl);
}
