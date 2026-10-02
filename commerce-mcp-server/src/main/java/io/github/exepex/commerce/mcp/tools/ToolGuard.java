package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.downstream.OrderApi;
import io.github.exepex.commerce.mcp.governance.AuditEvent;
import io.github.exepex.commerce.mcp.governance.AuditTrail;
import io.github.exepex.commerce.mcp.governance.GovernanceException;
import io.github.exepex.commerce.mcp.security.AgentRegistry;
import io.github.exepex.commerce.mcp.security.CallingAgent;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Wraps every tool call: checks that the calling agent may use the tool, runs it, and records the outcome in the
 * audit trail. A refused or failed call is recorded too, and its message goes back to the agent.
 */
@Component
class ToolGuard {

    private final AgentRegistry agents;
    private final AuditTrail audit;

    ToolGuard(AgentRegistry agents, AuditTrail audit) {
        this.agents = agents;
        this.audit = audit;
    }

    /**
     * @param orderId the order the call is about, for the audit trail; {@code null} if none
     * @param auditsItself whether the tool records its own, more specific, audit entry when it succeeds
     */
    <T> T run(McpTransportContext context, String tool, UUID orderId, String summary, boolean auditsItself,
            Function<String, T> action) {
        String agentId = CallingAgent.of(context);
        if (!agents.mayCall(agentId, tool)) {
            audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, tool, AuditEvent.Outcome.DENIED,
                    "Tool not permitted for this agent", summary);
            throw new GovernanceException(HttpStatus.FORBIDDEN, "Agent " + agentId + " is not permitted to call " + tool);
        }
        try {
            T result = action.apply(agentId);
            if (!auditsItself) {
                audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, tool, AuditEvent.Outcome.SUCCEEDED, summary, null);
            }
            return result;
        } catch (GovernanceException refused) {
            AuditEvent.Outcome outcome = refused.getStatusCode().value() == HttpStatus.FORBIDDEN.value()
                    ? AuditEvent.Outcome.DENIED
                    : AuditEvent.Outcome.FAILED;
            audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, tool, outcome, summary + ": " + refused.getMessage(), null);
            throw refused;
        } catch (RuntimeException failure) {
            audit.record(orderId, AuditEvent.ActorType.AGENT, agentId, tool, AuditEvent.Outcome.FAILED,
                    summary + ": " + failure.getMessage(), null);
            throw failure;
        }
    }

    /** A customer-facing agent may only touch the orders of the customer it is talking to. */
    void ensureCustomerOwns(String agentId, OrderApi.Order order, String customerEmail) {
        if (agents.isCustomerScoped(agentId)
                && (customerEmail == null || !customerEmail.equalsIgnoreCase(order.customerEmail()))) {
            throw new GovernanceException(HttpStatus.FORBIDDEN,
                    "Order " + order.id() + " does not belong to the customer in this conversation");
        }
    }

    static String requireCustomer(String customerEmail) {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "A customer email is required");
        }
        return customerEmail;
    }

    static UUID parseOrderId(String orderId) {
        try {
            return UUID.fromString(orderId);
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "'" + orderId + "' is not an order id");
        }
    }
}
