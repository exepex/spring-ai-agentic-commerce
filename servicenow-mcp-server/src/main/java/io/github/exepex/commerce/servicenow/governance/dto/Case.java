package io.github.exepex.commerce.servicenow.governance.dto;

import java.util.UUID;

/**
 * A shop case; {@code incidentNumber} and {@code incidentUrl} are empty until its incident is opened. The link, not
 * the number, says which incident it is: numbers repeat across instances. {@code forPeople} means its incident goes
 * straight to the default team, not to the agent; missing means no.
 */
public record Case(UUID id, UUID orderId, String type, String status, String title, String description,
        String incidentNumber, String incidentUrl, String assignmentGroup, Boolean forPeople) {

    public boolean isForPeople() {
        return Boolean.TRUE.equals(forPeople);
    }
}
