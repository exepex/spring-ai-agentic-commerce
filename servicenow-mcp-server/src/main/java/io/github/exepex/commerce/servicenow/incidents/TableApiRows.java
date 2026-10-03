package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.constants.ServiceNowFields;
import io.github.exepex.commerce.servicenow.constants.TableApi;
import io.github.exepex.commerce.servicenow.constants.ToolResults;
import io.github.exepex.commerce.servicenow.incidents.dto.Incident;
import io.github.exepex.commerce.servicenow.incidents.dto.Journal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * How the Table API's answers are read: an incident from its row, read with {@code sysparm_display_value=all}, and an
 * incident's journal fields as ServiceNow shows them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TableApiRows {

    private static final DateTimeFormatter SERVICENOW_TIME = DateTimeFormatter.ofPattern(ServiceNowFields.TIME_PATTERN);
    /**
     * How much of each journal field is kept, newest first: enough for every note of a working incident, while a
     * long-lived incident's history cannot flood the agent.
     */
    private static final int MAX_JOURNAL_LENGTH = 20_000;

    static List<Incident> incidentsOf(JsonNode body) {
        return StreamSupport.stream(body.path(TableApi.RESULT).spliterator(), false)
                .map(TableApiRows::incidentOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    static Incident incidentOf(JsonNode row) {
        return new Incident(value(row, ServiceNowFields.SYS_ID), value(row, ServiceNowFields.NUMBER),
                value(row, ServiceNowFields.SHORT_DESCRIPTION), value(row, ServiceNowFields.DESCRIPTION),
                value(row, ServiceNowFields.STATE), display(row, ServiceNowFields.STATE),
                display(row, ServiceNowFields.ASSIGNMENT_GROUP), value(row, ServiceNowFields.ASSIGNED_TO),
                display(row, ServiceNowFields.ASSIGNED_TO), display(row, ServiceNowFields.CALLER_ID),
                value(row, ServiceNowFields.CORRELATION_ID).strip(),
                value(row, ServiceNowFields.CORRELATION_DISPLAY).strip(),
                utc(value(row, ServiceNowFields.SYS_CREATED_ON)), utc(value(row, ServiceNowFields.SYS_UPDATED_ON)));
    }

    /** The first row of an answer; a missing node when there is none. */
    static JsonNode firstRowOf(JsonNode body) {
        return body.path(TableApi.RESULT).path(0);
    }

    /** The journal fields of a row read with {@code sysparm_display_value=true}, each cut to its newest part. */
    static Journal journalOf(JsonNode row) {
        return new Journal(newest(row.path(ServiceNowFields.WORK_NOTES).asString("")),
                newest(row.path(ServiceNowFields.COMMENTS).asString("")));
    }

    /** All of a row's work notes as shown, not cut like {@link #journalOf}. */
    static String allWorkNotesOf(JsonNode row) {
        return row.path(ServiceNowFields.WORK_NOTES).asString("");
    }

    /** The newest part of a journal field: it is shown newest first, so older entries are cut from the end. */
    private static String newest(String shown) {
        var journal = shown.strip();
        return journal.length() <= MAX_JOURNAL_LENGTH ? journal
                : journal.substring(0, MAX_JOURNAL_LENGTH) + ToolResults.OLDER_ENTRIES_LEFT_OUT;
    }

    private static String value(JsonNode row, String field) {
        return row.path(field).path(TableApi.VALUE).asString("");
    }

    private static String display(JsonNode row, String field) {
        return row.path(field).path(TableApi.SHOWN_VALUE).asString("");
    }

    /** ServiceNow stores times in UTC as {@code yyyy-MM-dd HH:mm:ss}. */
    private static Instant utc(String serviceNowTime) {
        return serviceNowTime.isBlank() ? null : LocalDateTime.parse(serviceNowTime, SERVICENOW_TIME).toInstant(ZoneOffset.UTC);
    }
}
