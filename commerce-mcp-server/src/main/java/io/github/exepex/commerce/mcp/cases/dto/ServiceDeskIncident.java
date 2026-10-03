package io.github.exepex.commerce.mcp.cases.dto;

import io.github.exepex.commerce.mcp.cases.CaseType;
import io.github.exepex.commerce.mcp.cases.SupportCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** An incident the service desk raised about an order, as the poller found it; see {@link CaseType#SERVICE_DESK}. */
public record ServiceDeskIncident(@NotNull UUID orderId, @NotBlank @Size(max = 40) String number,
        @NotBlank @Size(max = 500) String url, @NotBlank @Size(max = 4000) String shortDescription,
        @NotNull SupportCase.Status status, @Size(max = 200) String assignmentGroup) {}
