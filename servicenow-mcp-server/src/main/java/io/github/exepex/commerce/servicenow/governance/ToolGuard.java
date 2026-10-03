package io.github.exepex.commerce.servicenow.governance;

import io.github.exepex.commerce.mcpserver.guard.GovernedToolCalls;
import io.github.exepex.commerce.mcpserver.guard.ToolCall;
import io.github.exepex.commerce.servicenow.exception.ToolRefusedException;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Every ServiceNow tool call goes through the shared governed tool call: the agent's allowlist, its kill switch
 * ({@link IncidentAgentKillSwitch}), the refusals the model reads ({@link ServiceNowToolRefusals}) and the audit trail
 * the commerce MCP server keeps ({@link ToolCallAuditTrail}).
 */
@Component
@RequiredArgsConstructor
public class ToolGuard {

    private final GovernedToolCalls governedToolCalls;

    /**
     * @param summary what the call does, for the audit trail, naming the incident
     * @throws ToolRefusedException when the agent may not make the call, or the tool refuses it
     */
    public <T> T run(McpTransportContext context, String tool, String summary, Function<String, T> action) {
        return governedToolCalls.run(context, ToolCall.of(tool, summary), action);
    }
}
