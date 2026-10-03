package io.github.exepex.commerce.servicenow.governance.dto;

import java.util.UUID;

/**
 * Who has the case's incident now; {@code status} is WITH_AGENT, WITH_TEAM or RESOLVED. {@code incidentFinal} means
 * a resolved incident is closed or cancelled, so it can no longer be reopened. {@code orderId} is the order the
 * incident names now, in its Correlation ID; null when it names none.
 */
public record IncidentState(String number, String status, String assignmentGroup, boolean incidentFinal, UUID orderId) {}
