package io.github.exepex.commerce.mcpserver.guard;

import io.github.exepex.commerce.mcpserver.security.AgentRegistry;
import io.github.exepex.commerce.mcpserver.security.CallingAgent;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.function.Function;

/**
 * Every tool call of every MCP server goes through here, in the same order: who is calling, whether its definition
 * lets it call the tool, whether it is switched on, and then the tool itself. Each step is recorded in the audit trail,
 * a refused or failed call too, and the refusal goes back to the model.
 */
public class GovernedToolCalls {

    private final AgentRegistry agents;
    private final KillSwitch killSwitch;
    private final ToolRefusals refusals;
    private final ToolCallAudit audit;

    public GovernedToolCalls(AgentRegistry agents, KillSwitch killSwitch, ToolRefusals refusals, ToolCallAudit audit) {
        this.agents = agents;
        this.killSwitch = killSwitch;
        this.refusals = refusals;
        this.audit = audit;
    }

    /** @param action the tool's work, given the calling agent's id */
    public <T> T run(McpTransportContext context, ToolCall call, Function<String, T> action) {
        var agentId = CallingAgent.of(context);
        if (!agents.mayCall(agentId, call.tool())) {
            audit.notPermitted(agentId, call);
            throw refusals.notPermitted(agentId, call.tool());
        }
        if (!killSwitch.worksWhileSwitchedOff(call.tool()) && !killSwitch.isSwitchedOn(agentId)) {
            audit.switchedOff(agentId, call);
            throw refusals.switchedOff(agentId);
        }
        try {
            var result = action.apply(agentId);
            if (!call.auditsItself()) {
                audit.succeeded(agentId, call);
            }
            return result;
        } catch (RuntimeException stopped) {
            audit.stopped(agentId, call, refusals.outcomeOf(stopped), stopped.getMessage());
            throw stopped;
        }
    }
}
