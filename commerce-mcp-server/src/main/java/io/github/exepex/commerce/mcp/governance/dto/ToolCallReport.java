package io.github.exepex.commerce.mcp.governance.dto;

import io.github.exepex.commerce.mcp.governance.AuditEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A tool call an agent made on another MCP server, such as ServiceNow's, reported by that server with the agent's own
 * token. {@code action} is the tool, prefixed with the server's name.
 */
public record ToolCallReport(UUID orderId, @NotBlank @Size(max = 100) String action,
        @NotNull AuditEvent.Outcome outcome, @NotBlank String summary, String details) {}
