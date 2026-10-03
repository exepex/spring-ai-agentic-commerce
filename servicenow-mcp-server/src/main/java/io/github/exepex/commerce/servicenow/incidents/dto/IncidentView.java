package io.github.exepex.commerce.servicenow.incidents.dto;

import java.time.Instant;

/** An incident as {@code get_incident} shows it to the agent, with its work notes and comments. */
public record IncidentView(String number, String shortDescription, String description, String state, String caller,
        String assignmentGroup, String assignedTo, String linkedOrderId, Instant openedAt, String workNotes,
        String comments) {}
