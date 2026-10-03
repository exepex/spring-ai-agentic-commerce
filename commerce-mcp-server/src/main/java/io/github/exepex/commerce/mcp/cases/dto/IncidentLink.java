package io.github.exepex.commerce.mcp.cases.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The incident ServiceNow created for a case, as the poller reports it. */
public record IncidentLink(@NotBlank @Size(max = 40) String number, @Size(max = 500) String url) {}
