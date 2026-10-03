package io.github.exepex.commerce.agent.dto;

import java.util.List;

/** One agent as the agents API shows it; {@code enabled} is {@code null} when its switch could not be read. */
public record AgentView(String id, Boolean enabled, String model, String effort, List<String> tools) {}
