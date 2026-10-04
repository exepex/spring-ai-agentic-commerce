package io.github.exepex.commerce.mcp.governance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Each change of a proposal's status is one conditional statement, so of two concurrent changes only one applies. */
interface OrderProposalRepository extends JpaRepository<OrderProposal, UUID> {

    /** Claims a proposed order for the customer's confirmation; 0 if it was already confirmed or is being confirmed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update OrderProposal p set p.status = io.github.exepex.commerce.mcp.governance.OrderProposal.Status.CONFIRMING,
                p.paymentMethod = :paymentMethod, p.confirmingSince = :now
            where p.id = :id and p.status = io.github.exepex.commerce.mcp.governance.OrderProposal.Status.PROPOSED""")
    int claimForConfirmation(@Param("id") UUID id, @Param("paymentMethod") String paymentMethod, @Param("now") Instant now);

    /** Links the order whose payment is not settled yet; the proposal stays {@code CONFIRMING}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update OrderProposal p set p.orderId = :orderId
            where p.id = :id and p.status = io.github.exepex.commerce.mcp.governance.OrderProposal.Status.CONFIRMING""")
    int linkPendingOrder(@Param("id") UUID id, @Param("orderId") UUID orderId);

    /** Ends a confirmation as confirmed or failed; 0 if another request already ended it. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update OrderProposal p set p.status = :outcome, p.orderId = :orderId, p.failure = :failure
            where p.id = :id and p.status = io.github.exepex.commerce.mcp.governance.OrderProposal.Status.CONFIRMING""")
    int settle(@Param("id") UUID id, @Param("outcome") OrderProposal.Status outcome, @Param("orderId") UUID orderId,
            @Param("failure") String failure);

    List<OrderProposal> findTop200ByStatusAndConfirmingSinceBeforeOrderByConfirmingSince(OrderProposal.Status status,
            Instant confirmingBefore);
}
