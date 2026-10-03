package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.constants.ToolMessages;
import io.github.exepex.commerce.mcp.constants.ToolNames;
import io.github.exepex.commerce.mcp.downstream.dto.Order;
import io.github.exepex.commerce.mcp.exception.AgentSwitchedOffException;
import io.github.exepex.commerce.mcp.exception.CustomerScopeViolationException;
import io.github.exepex.commerce.mcp.exception.DownstreamException;
import io.github.exepex.commerce.mcp.exception.GovernanceException;
import io.github.exepex.commerce.mcp.exception.ToolNotPermittedException;
import io.github.exepex.commerce.mcp.governance.AgentSwitches;
import io.github.exepex.commerce.mcp.governance.AuditEvent;
import io.github.exepex.commerce.mcp.governance.AuditTrail;
import io.github.exepex.commerce.mcp.security.AgentRegistry;
import io.github.exepex.commerce.mcp.security.CallingAgent;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Wraps every tool call: checks that the calling agent may use the tool and is switched on, runs it, and records the
 * outcome in the audit trail. A refused or failed call is recorded too, and its message goes back to the agent. A
 * switched-off agent can still hand work to a human, so nothing is left without someone handling it.
 */
@Component
@RequiredArgsConstructor
class ToolGuard {

    private final AgentRegistry agents;
    private final AgentSwitches switches;
    private final AuditTrail audit;

    /**
     * @param orderId the order the call is about, for the audit trail; {@code null} if none
     * @param auditsItself whether the tool records its own, more specific, audit entry when it succeeds
     */
    <T> T run(McpTransportContext context, String tool, UUID orderId, String summary, boolean auditsItself,
            Function<String, T> action) {
        var agentId = CallingAgent.of(context);
        if (!agents.mayCall(agentId, tool)) {
            audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, tool, AuditEvent.Outcome.DENIED,
                    ToolMessages.NOT_PERMITTED, summary);
            throw new ToolNotPermittedException(agentId, tool);
        }
        if (!ToolNames.ESCALATE_TO_HUMAN.equals(tool) && !switches.isEnabled(agentId)) {
            audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, tool, AuditEvent.Outcome.DENIED,
                    ToolMessages.SWITCHED_OFF, summary);
            throw new AgentSwitchedOffException(agentId);
        }
        try {
            var result = action.apply(agentId);
            if (!auditsItself) {
                audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, tool, AuditEvent.Outcome.SUCCEEDED, summary, null);
            }
            return result;
        } catch (GovernanceException refused) {
            audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, tool, outcomeOf(refused),
                    ToolMessages.STOPPED_CALL.formatted(summary, refused.getMessage()), null);
            throw refused;
        } catch (RuntimeException failure) {
            audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, tool, AuditEvent.Outcome.FAILED,
                    ToolMessages.STOPPED_CALL.formatted(summary, failure.getMessage()), null);
            throw failure;
        }
    }

    /** A customer-facing agent may only touch the orders of the customer it is talking to. */
    boolean isCustomerScoped(String agentId) {
        return agents.isCustomerScoped(agentId);
    }

    void ensureCustomerOwns(String agentId, Order order, String customerEmail) {
        if (agents.isCustomerScoped(agentId)
                && (customerEmail == null || !customerEmail.equalsIgnoreCase(order.customerEmail()))) {
            throw new CustomerScopeViolationException(order.id());
        }
    }

    /**
     * A rule that forbids the call denies it; anything else that stops it is a failure, a commerce service's refusal
     * too, whatever status that service answered with.
     */
    private static AuditEvent.Outcome outcomeOf(GovernanceException refused) {
        return !(refused instanceof DownstreamException) && refused.getStatus().value() == HttpStatus.FORBIDDEN.value()
                ? AuditEvent.Outcome.DENIED
                : AuditEvent.Outcome.FAILED;
    }
}
