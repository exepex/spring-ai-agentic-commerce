package io.github.exepex.commerce.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Switches an agent on or off, and who did it. */
public record SwitchChange(@NotNull Boolean enabled, @NotBlank @Size(max = 100) String by) {}
