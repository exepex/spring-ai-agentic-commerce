package io.github.exepex.commerce.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Turns an agent's kill switch on or off; {@code by} is the person switching, for the audit trail. */
public record Switch(boolean enabled, @NotBlank @Size(max = 100) String by) {}
