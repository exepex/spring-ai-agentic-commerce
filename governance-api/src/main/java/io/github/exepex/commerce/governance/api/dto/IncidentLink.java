package io.github.exepex.commerce.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The ServiceNow incident opened for a case. */
public record IncidentLink(@NotBlank @Size(max = 40) String number, @Size(max = 500) String url) {}
