package io.github.exepex.commerce.agent;

import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

/** The agents' kill switches, kept by the commerce MCP server: each agent's id and whether it is on. */
@HttpExchange("/api/agent-switches")
public interface AgentSwitchesApi {

    record Change(boolean enabled, String by) {}

    @GetExchange
    Map<String, Boolean> all();

    @PutExchange("/{agentId}")
    Map<String, Boolean> set(@PathVariable String agentId, @RequestBody Change change);
}
