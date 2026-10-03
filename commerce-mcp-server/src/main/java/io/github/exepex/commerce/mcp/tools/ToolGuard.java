package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.mcp.downstream.dto.Order;
import io.github.exepex.commerce.mcp.exception.CustomerScopeViolationException;
import io.github.exepex.commerce.mcpserver.guard.GovernedToolCalls;
import io.github.exepex.commerce.mcpserver.guard.ToolCall;
import io.github.exepex.commerce.mcpserver.security.AgentRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Wraps every tool call: checks that the calling agent may use the tool and is switched on, runs it, and records the
 * outcome in the audit trail. A refused or failed call is recorded too, and its message goes back to the agent. A
 * switched-off agent can still hand work to a human, so nothing is left without someone handling it.
 *
 * <p>The checks run in {@link GovernedToolCalls}, as on every MCP server of the shop; this server keeps its switches
 * in {@link AgentKillSwitch}, words its refusals in {@link CommerceToolRefusals} and records each call through
 * {@link ToolCallAuditTrail}. Customer scoping is this server's own rule, applied here.
 */
@Component
@RequiredArgsConstructor
class ToolGuard {

    private final GovernedToolCalls calls;
    private final AgentRegistry agents;

    /**
     * @param orderId the order the call is about, for the audit trail; {@code null} if none
     * @param auditsItself whether the tool records its own, more specific, audit entry when it succeeds
     */
    <T> T run(McpTransportContext context, String tool, UUID orderId, String summary, boolean auditsItself,
            Function<String, T> action) {
        return calls.run(context, new ToolCall(tool, summary, orderId, auditsItself), action);
    }

    /** A customer-facing agent may only touch the orders of the customer it is talking to. */
    boolean isCustomerScoped(String agentId) {
        return agents.definitionOf(agentId).map(AgentDefinition::customerScoped).orElse(false);
    }

    void ensureCustomerOwns(String agentId, Order order, String customerEmail) {
        if (isCustomerScoped(agentId)
                && (customerEmail == null || !customerEmail.equalsIgnoreCase(order.customerEmail()))) {
            throw new CustomerScopeViolationException(order.id());
        }
    }
}
