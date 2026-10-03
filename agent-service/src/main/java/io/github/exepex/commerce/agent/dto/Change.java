package io.github.exepex.commerce.agent.dto;

/** A change of an agent's kill switch, sent to the commerce MCP server, which keeps the switches. */
public record Change(boolean enabled, String by) {}
