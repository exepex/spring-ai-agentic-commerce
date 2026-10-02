package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.security.AgentAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The governance API: the UI reads the audit trail and acts on approvals, proposals and escalations; agents record
 * their decisions under {@code /api/agent}, authenticated with their bearer token. The UI side has no login in this
 * demo; in production it would sit behind the organisation's identity provider.
 */
@RestController
class GovernanceController {

    record Decision(@NotBlank String by, String note) {}

    record Confirmation(String paymentMethod) {}

    record AgentDecision(UUID orderId, @NotBlank String summary, String reasoning, String model, Long inputTokens,
            Long outputTokens, Long durationMillis) {}

    private final AuditTrail audit;
    private final RefundService refunds;
    private final ProposalService proposals;
    private final NotificationService notifications;
    private final EscalationService escalations;

    GovernanceController(AuditTrail audit, RefundService refunds, ProposalService proposals,
            NotificationService notifications, EscalationService escalations) {
        this.audit = audit;
        this.refunds = refunds;
        this.proposals = proposals;
        this.notifications = notifications;
        this.escalations = escalations;
    }

    @GetMapping("/api/orders/{orderId}/timeline")
    List<AuditEvent> timeline(@PathVariable UUID orderId) {
        return audit.timelineOf(orderId);
    }

    @GetMapping("/api/audit-events")
    List<AuditEvent> recentAuditEvents() {
        return audit.recent();
    }

    @GetMapping("/api/refund-requests")
    List<RefundRequest> refundRequests(@RequestParam(required = false) RefundRequest.Status status,
            @RequestParam(required = false) UUID orderId) {
        if (orderId != null) {
            return refunds.forOrder(orderId);
        }
        return status == null ? refunds.recent() : refunds.withStatus(status);
    }

    @PostMapping("/api/refund-requests/{requestId}/approve")
    RefundRequest approveRefund(@PathVariable UUID requestId, @Valid @RequestBody Decision decision) {
        return refunds.approve(requestId, decision.by(), decision.note());
    }

    @PostMapping("/api/refund-requests/{requestId}/reject")
    RefundRequest rejectRefund(@PathVariable UUID requestId, @Valid @RequestBody Decision decision) {
        return refunds.reject(requestId, decision.by(), decision.note());
    }

    @PostMapping("/api/refund-requests/{requestId}/retry")
    RefundRequest retryRefund(@PathVariable UUID requestId, @Valid @RequestBody Decision decision) {
        return refunds.retry(requestId, decision.by());
    }

    @GetMapping("/api/order-proposals/{proposalId}")
    ProposalService.Proposal proposal(@PathVariable UUID proposalId) {
        return proposals.get(proposalId);
    }

    /** The customer's own confirmation of an order an agent proposed. */
    @PostMapping("/api/order-proposals/{proposalId}/confirm")
    ProposalService.Proposal confirmProposal(@PathVariable UUID proposalId, @RequestBody(required = false) Confirmation confirmation) {
        String paymentMethod = confirmation == null || confirmation.paymentMethod() == null
                ? "pm_card_visa"
                : confirmation.paymentMethod();
        return proposals.confirm(proposalId, paymentMethod);
    }

    @GetMapping("/api/notifications")
    List<CustomerNotification> notifications(@RequestParam(required = false) UUID orderId) {
        return orderId == null ? notifications.recent() : notifications.forOrder(orderId);
    }

    @GetMapping("/api/escalations")
    List<Escalation> escalations(@RequestParam(required = false) Escalation.Status status) {
        return status == null ? escalations.recent() : escalations.withStatus(status);
    }

    @PostMapping("/api/escalations/{escalationId}/assign")
    Escalation assignEscalation(@PathVariable UUID escalationId, @Valid @RequestBody Decision decision) {
        return escalations.assign(escalationId, decision.by());
    }

    @PostMapping("/api/escalations/{escalationId}/hand-back")
    Escalation handBackEscalation(@PathVariable UUID escalationId, @Valid @RequestBody Decision decision) {
        return escalations.handBack(escalationId, decision.by(), decision.note());
    }

    @PostMapping("/api/escalations/{escalationId}/resolve")
    Escalation resolveEscalation(@PathVariable UUID escalationId, @Valid @RequestBody Decision decision) {
        return escalations.resolve(escalationId, decision.by(), decision.note());
    }

    /** An agent records why it did what it did, with the model and token usage behind it. */
    @PostMapping("/api/agent/decisions")
    void recordDecision(@RequestAttribute(AgentAuthenticationFilter.AGENT_ID_ATTRIBUTE) String agentId,
            @Valid @RequestBody AgentDecision decision) {
        String usage = decision.model() == null ? null : "Model " + decision.model() + ", " + decision.inputTokens()
                + " input and " + decision.outputTokens() + " output tokens, " + decision.durationMillis() + " ms";
        audit.record(decision.orderId(), AuditEvent.ActorType.AGENT, agentId, "decision", AuditEvent.Outcome.SUCCEEDED,
                decision.summary(), usage == null ? decision.reasoning() : usage + "\n\n" + decision.reasoning());
    }
}
