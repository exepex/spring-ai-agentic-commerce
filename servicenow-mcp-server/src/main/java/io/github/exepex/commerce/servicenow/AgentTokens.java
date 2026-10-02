package io.github.exepex.commerce.servicenow;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Each agent's bearer token, by agent id, under {@code commerce.agents}: the same secrets the commerce MCP server
 * accepts. What each agent may call here comes from the {@code servicenow} tools in its definition.
 */
@ConfigurationProperties("commerce")
public record AgentTokens(Map<String, Agent> agents) {

    /** @param token the bearer token the agent authenticates with */
    public record Agent(String token) {}
}
