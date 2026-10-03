package io.github.exepex.commerce.servicenow.incidents.dto;

/**
 * An incident's work notes and comments as ServiceNow shows them: newest entry first, each starting with a line
 * {@code <time> - <who> (<kind>)}, the time in the integration user's time zone.
 */
public record Journal(String workNotes, String comments) {}
