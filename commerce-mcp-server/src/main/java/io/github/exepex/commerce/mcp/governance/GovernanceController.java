package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.constants.ApiPaths;
import io.github.exepex.commerce.mcp.constants.AuditActions;
import io.github.exepex.commerce.mcp.constants.AuditSummaries;
import io.github.exepex.commerce.mcp.constants.DownstreamApis;
import io.github.exepex.commerce.mcp.constants.SecurityValues;
import io.github.exepex.commerce.mcp.governance.dto.AgentDecision;
import io.github.exepex.commerce.mcp.governance.dto.Confirmation;
import io.github.exepex.commerce.mcp.governance.dto.Decision;
import io.github.exepex.commerce.mcp.governance.dto.Proposal;
import io.github.exepex.commerce.mcp.governance.dto.SwitchChange;
import io.github.exepex.commerce.mcp.governance.dto.ToolCallReport;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The governance API: the UI reads the audit trail and acts on approvals, proposals and the agents' kill switches;
 * agents record their decisions under {@code /api/agent}, authenticated with their bearer token. The UI side has no
 * login in this demo; in production it would sit behind the organisation's identity provider.
 */
@RestController
@RequiredArgsConstructor
class GovernanceController {

    private final AuditTrail audit;
    private final RefundService refunds;
    private final ProposalService proposals;
    private final NotificationService notifications;
    private final AgentSwitches switches;

    @GetMapping(ApiPaths.ORDER_TIMELINE)
    List<AuditEvent> timeline(@PathVariable UUID orderId) {
        return audit.timelineOf(orderId);
    }

    @GetMapping(ApiPaths.AUDIT_EVENTS)
    List<AuditEvent> recentAuditEvents() {
        return audit.recent();
    }

    @GetMapping(ApiPaths.REFUND_REQUESTS)
    List<RefundRequest> refundRequests(@RequestParam(required = false) RefundRequest.Status status,
            @RequestParam(required = false) UUID orderId) {
        if (orderId != null) {
            return refunds.forOrder(orderId);
        }
        return status == null ? refunds.recent() : refunds.withStatus(status);
    }

    @PostMapping(ApiPaths.APPROVE_REFUND)
    RefundRequest approveRefund(@PathVariable UUID requestId, @Valid @RequestBody Decision decision) {
        return refunds.approve(requestId, decision.by(), decision.note());
    }

    @PostMapping(ApiPaths.REJECT_REFUND)
    RefundRequest rejectRefund(@PathVariable UUID requestId, @Valid @RequestBody Decision decision) {
        return refunds.reject(requestId, decision.by(), decision.note());
    }

    @PostMapping(ApiPaths.RETRY_REFUND)
    RefundRequest retryRefund(@PathVariable UUID requestId, @Valid @RequestBody Decision decision) {
        return refunds.retry(requestId, decision.by());
    }

    @GetMapping(ApiPaths.ORDER_PROPOSAL)
    Proposal proposal(@PathVariable UUID proposalId) {
        return proposals.get(proposalId);
    }

    /** The customer's own confirmation of an order an agent proposed. */
    @PostMapping(ApiPaths.CONFIRM_PROPOSAL)
    Proposal confirmProposal(@PathVariable UUID proposalId, @RequestBody(required = false) Confirmation confirmation) {
        var paymentMethod = confirmation == null || confirmation.paymentMethod() == null
                ? DownstreamApis.DEFAULT_PAYMENT_METHOD
                : confirmation.paymentMethod();
        return proposals.confirm(proposalId, paymentMethod);
    }

    @GetMapping(ApiPaths.NOTIFICATIONS)
    List<CustomerNotification> notifications(@RequestParam(required = false) UUID orderId) {
        return orderId == null ? notifications.recent() : notifications.forOrder(orderId);
    }

    @GetMapping(ApiPaths.AGENT_SWITCHES)
    Map<String, Boolean> agentSwitches() {
        return switches.all();
    }

    @PutMapping(ApiPaths.AGENT_SWITCH)
    Map<String, Boolean> switchAgent(@PathVariable String agentId, @Valid @RequestBody SwitchChange change) {
        return switches.set(agentId, change.enabled(), change.by());
    }

    @PostMapping(ApiPaths.TOOL_CALLS)
    void recordToolCall(@RequestAttribute(SecurityValues.AGENT_ID_ATTRIBUTE) String agentId,
            @Valid @RequestBody ToolCallReport call) {
        audit.record(call.orderId(), AuditEvent.ActorType.AGENT, agentId, call.action(), call.outcome(), call.summary(),
                call.details());
    }

    /** An agent records why it did what it did, with the model and token usage behind it. */
    @PostMapping(ApiPaths.DECISIONS)
    void recordDecision(@RequestAttribute(SecurityValues.AGENT_ID_ATTRIBUTE) String agentId,
            @Valid @RequestBody AgentDecision decision) {
        var usage = decision.model() == null ? null : AuditSummaries.DECISION_USAGE.formatted(decision.model(),
                decision.inputTokens(), decision.outputTokens(), decision.durationMillis());
        audit.record(decision.orderId(), AuditEvent.ActorType.AGENT, agentId, AuditActions.DECISION,
                AuditEvent.Outcome.SUCCEEDED, decision.summary(),
                usage == null ? decision.reasoning() : AuditSummaries.DECISION_DETAILS.formatted(usage,
                        decision.reasoning()));
    }
}
