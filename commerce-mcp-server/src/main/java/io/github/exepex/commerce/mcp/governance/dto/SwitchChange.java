package io.github.exepex.commerce.mcp.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Switches an agent on or off. {@code by} is recorded as the audit entry's actor, which holds 100 characters. */
public record SwitchChange(@NotNull Boolean enabled, @NotBlank @Size(max = 100) String by) {}
