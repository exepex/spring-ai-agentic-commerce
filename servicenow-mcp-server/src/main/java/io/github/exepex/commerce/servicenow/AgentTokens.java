package io.github.exepex.commerce.servicenow;

import io.github.exepex.commerce.servicenow.constants.ConfigKeys;
import io.github.exepex.commerce.servicenow.dto.Agent;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Each agent's bearer token, by agent id, under {@code commerce.agents}: the same secrets the commerce MCP server
 * accepts. What each agent may call here comes from the {@code servicenow} tools in its definition.
 */
@ConfigurationProperties(ConfigKeys.COMMERCE_PREFIX)
public record AgentTokens(Map<String, Agent> agents) {}
