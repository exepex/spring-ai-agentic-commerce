package io.github.exepex.commerce.mcp.dto;

/**
 * An agent's credentials, set under {@code commerce.governance.agents.<agent id>}.
 *
 * @param token the bearer token the agent authenticates with
 */
public record Agent(String token) {}
