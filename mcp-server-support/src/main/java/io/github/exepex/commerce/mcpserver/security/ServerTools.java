package io.github.exepex.commerce.mcpserver.security;

import io.github.exepex.commerce.agents.AgentDefinition;
import java.util.List;

/** Which of this server's tools an agent's definition lets it call. Each MCP server declares one. */
@FunctionalInterface
public interface ServerTools {

    List<String> of(AgentDefinition definition);
}
