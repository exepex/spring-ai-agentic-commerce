package io.github.exepex.commerce.mcp.governance.dto;

import jakarta.validation.constraints.NotBlank;

/** A person's decision on a refund request: who decided, and why. */
public record Decision(@NotBlank String by, String note) {}
