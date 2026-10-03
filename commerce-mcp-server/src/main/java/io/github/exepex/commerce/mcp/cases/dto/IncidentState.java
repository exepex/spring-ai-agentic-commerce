package io.github.exepex.commerce.mcp.cases.dto;

import io.github.exepex.commerce.mcp.cases.SupportCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Who has a case's incident now. {@code number} is the incident the state was read from; it must be the case's own.
 * {@code incidentFinal} means a resolved one is closed or cancelled, and {@code orderId} is the order the incident
 * names now, if any.
 */
public record IncidentState(@NotBlank @Size(max = 40) String number, @NotNull SupportCase.Status status,
        @Size(max = 200) String assignmentGroup, boolean incidentFinal, UUID orderId) {}
