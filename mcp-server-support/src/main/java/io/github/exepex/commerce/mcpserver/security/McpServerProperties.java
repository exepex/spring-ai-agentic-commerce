package io.github.exepex.commerce.mcpserver.security;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Who may call an MCP server, under {@code commerce.mcp}.
 *
 * @param agents each agent's bearer token, by agent id; what the agent may call comes from its definition
 * @param protectedPaths the paths only an authenticated agent may use: the MCP endpoint, and any API agents report to
 */
@ConfigurationProperties("commerce.mcp")
public record McpServerProperties(Map<String, AgentToken> agents, @DefaultValue("/mcp") List<String> protectedPaths) {}
