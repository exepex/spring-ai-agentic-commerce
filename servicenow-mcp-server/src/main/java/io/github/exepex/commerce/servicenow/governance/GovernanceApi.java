package io.github.exepex.commerce.servicenow.governance;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The commerce MCP server's governance API: the agents' kill switches and the audit trail they all share. */
public interface GovernanceApi {

    /** One tool call, recorded as the agent that made it; {@code outcome} is SUCCEEDED, FAILED or DENIED. */
    record ToolCall(UUID orderId, String action, String outcome, String summary, String details) {}

    @GetExchange("/api/agent-switches")
    Map<String, Boolean> switches();

    @PostExchange("/api/agent/tool-calls")
    void recordToolCall(@RequestHeader("Authorization") String authorization, @RequestBody ToolCall call);
}
