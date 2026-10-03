package io.github.exepex.commerce.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A tool call an agent made outside the commerce MCP server, for the audit trail under the agent's own name.
 *
 * @param orderId the order the call is about; null if none
 */
public record ToolCallReport(UUID orderId, @NotBlank @Size(max = 100) String action, @NotNull ToolCallOutcome outcome,
        @NotBlank String summary, String details) {}
