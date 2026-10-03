package io.github.exepex.commerce.governance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Who has a case's incident now, as ServiceNow says.
 *
 * @param incidentFinal whether a resolved incident is closed or cancelled, so it can no longer be reopened
 * @param orderId the order the incident names now, if any
 */
public record IncidentState(@NotBlank @Size(max = 40) String number, @NotNull CaseStatus status,
        @Size(max = 200) String assignmentGroup, boolean incidentFinal, UUID orderId) {}
