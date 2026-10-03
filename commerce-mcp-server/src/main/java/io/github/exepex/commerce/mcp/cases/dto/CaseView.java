package io.github.exepex.commerce.mcp.cases.dto;

import io.github.exepex.commerce.mcp.cases.CaseType;
import io.github.exepex.commerce.mcp.cases.SupportCase;
import java.time.Instant;
import java.util.UUID;

/**
 * A case as the case API shows it. {@code forPeople} means its incident goes straight to the default team, not to the
 * agent.
 */
public record CaseView(UUID id, UUID orderId, CaseType type, SupportCase.Status status, String title,
        String description, String raisedBy, String incidentNumber, String incidentUrl, String assignmentGroup,
        boolean forPeople, Instant createdAt, Instant updatedAt) {}
