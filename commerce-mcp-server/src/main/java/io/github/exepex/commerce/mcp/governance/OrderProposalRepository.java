package io.github.exepex.commerce.mcp.governance;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface OrderProposalRepository extends JpaRepository<OrderProposal, UUID> {

    /** Moves the proposal from one status to another in one statement; 0 if it was no longer in {@code from}. */
    @Modifying
    @Transactional
    @Query("update OrderProposal p set p.status = :to where p.id = :id and p.status = :from")
    int moveStatus(@Param("id") UUID id, @Param("from") OrderProposal.Status from, @Param("to") OrderProposal.Status to);
}
