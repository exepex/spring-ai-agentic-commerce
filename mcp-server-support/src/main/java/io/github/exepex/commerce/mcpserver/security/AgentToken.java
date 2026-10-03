package io.github.exepex.commerce.mcpserver.security;

/** @param token the bearer token the agent presents to the MCP server */
public record AgentToken(String token) {}
