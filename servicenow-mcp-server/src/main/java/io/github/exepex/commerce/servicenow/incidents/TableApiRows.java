package io.github.exepex.commerce.servicenow.incidents;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * How the Table API's answers are read: an incident from its row, read with {@code sysparm_display_value=all}, and an
 * incident's journal fields as ServiceNow shows them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TableApiRows {

    /** The incident fields every incident read asks for. */
    static final String INCIDENT_FIELDS = "sys_id,number,short_description,description,state,assignment_group,"
            + "assigned_to,caller_id,correlation_id,correlation_display,sys_created_on,sys_updated_on";

    private static final DateTimeFormatter SERVICENOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * How much of each journal field is kept, newest first: enough for every note of a working incident, while a
     * long-lived incident's history cannot flood the agent.
     */
    private static final int MAX_JOURNAL_LENGTH = 20_000;

    static List<ServiceNowClient.Incident> incidentsOf(JsonNode body) {
        List<ServiceNowClient.Incident> incidents = new ArrayList<>();
        for (JsonNode row : body.path("result")) {
            incidents.add(incidentOf(row));
        }
        return incidents;
    }

    static ServiceNowClient.Incident incidentOf(JsonNode row) {
        return new ServiceNowClient.Incident(value(row, "sys_id"), value(row, "number"), value(row, "short_description"),
                value(row, "description"), value(row, "state"), display(row, "state"), display(row, "assignment_group"),
                value(row, "assigned_to"), display(row, "assigned_to"), display(row, "caller_id"),
                value(row, "correlation_id").strip(), value(row, "correlation_display").strip(),
                utc(value(row, "sys_created_on")), utc(value(row, "sys_updated_on")));
    }

    /** The first row of an answer; a missing node when there is none. */
    static JsonNode firstRowOf(JsonNode body) {
        return body.path("result").path(0);
    }

    /** The journal fields of a row read with {@code sysparm_display_value=true}, each cut to its newest part. */
    static ServiceNowClient.Journal journalOf(JsonNode row) {
        return new ServiceNowClient.Journal(newest(row.path("work_notes").asString("")),
                newest(row.path("comments").asString("")));
    }

    /** All of a row's work notes as shown, not cut like {@link #journalOf}. */
    static String allWorkNotesOf(JsonNode row) {
        return row.path("work_notes").asString("");
    }

    /** The id a Correlation field holds; null when it holds none, or text that is not an id. */
    static UUID idOrNull(String text) {
        try {
            return text.isBlank() ? null : UUID.fromString(text.strip());
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    /** The newest part of a journal field: it is shown newest first, so older entries are cut from the end. */
    private static String newest(String shown) {
        String journal = shown.strip();
        return journal.length() <= MAX_JOURNAL_LENGTH ? journal
                : journal.substring(0, MAX_JOURNAL_LENGTH) + "\n[Older entries left out.]";
    }

    private static String value(JsonNode row, String field) {
        return row.path(field).path("value").asString("");
    }

    private static String display(JsonNode row, String field) {
        return row.path(field).path("display_value").asString("");
    }

    /** ServiceNow stores times in UTC as {@code yyyy-MM-dd HH:mm:ss}. */
    private static Instant utc(String serviceNowTime) {
        return serviceNowTime.isBlank() ? null : LocalDateTime.parse(serviceNowTime, SERVICENOW_TIME).toInstant(ZoneOffset.UTC);
    }
}
