package io.github.exepex.commerce.governance.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A case, and who has its incident.
 *
 * @param type the kind of problem, such as {@code STOCK_OUT} or {@code SERVICE_DESK}
 * @param forPeople whether the case is meant for people from the start, so the agent does not work it; a reader
 *     takes a missing value as false
 */
public record CaseView(UUID id, UUID orderId, String type, CaseStatus status, String title, String description,
        String raisedBy, String incidentNumber, String incidentUrl, String assignmentGroup, Boolean forPeople,
        Instant createdAt, Instant updatedAt) {}
