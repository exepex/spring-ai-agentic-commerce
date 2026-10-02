package io.github.exepex.commerce.mcp.governance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EscalationRepository extends JpaRepository<Escalation, UUID> {

    List<Escalation> findByStatusOrderByCreatedAt(Escalation.Status status);

    List<Escalation> findTop100ByOrderByCreatedAtDesc();

    List<Escalation> findByOrderIdAndStatusIn(UUID orderId, List<Escalation.Status> statuses);

    /** Assigns an open escalation in one statement; 0 if someone else already took it. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Escalation e set e.status = io.github.exepex.commerce.mcp.governance.Escalation.Status.ASSIGNED, e.assignedTo = :person, e.assignedAt = :now
            where e.id = :id and e.status = io.github.exepex.commerce.mcp.governance.Escalation.Status.OPEN""")
    int assign(@Param("id") UUID id, @Param("person") String person, @Param("now") Instant now);

    /** Puts an escalation back in the queue; 0 unless it is assigned to this person. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Escalation e set e.status = io.github.exepex.commerce.mcp.governance.Escalation.Status.OPEN, e.assignedTo = null, e.assignedAt = null
            where e.id = :id and e.status = io.github.exepex.commerce.mcp.governance.Escalation.Status.ASSIGNED and e.assignedTo = :person""")
    int handBack(@Param("id") UUID id, @Param("person") String person);

    /** Resolves an escalation; 0 unless it is assigned to this person. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Escalation e set e.status = io.github.exepex.commerce.mcp.governance.Escalation.Status.RESOLVED, e.resolvedBy = :person, e.resolutionNote = :note,
            e.resolvedAt = :now where e.id = :id and e.status = io.github.exepex.commerce.mcp.governance.Escalation.Status.ASSIGNED and e.assignedTo = :person""")
    int resolve(@Param("id") UUID id, @Param("person") String person, @Param("note") String note,
            @Param("now") Instant now);
}
