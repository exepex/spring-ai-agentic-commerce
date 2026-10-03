package io.github.exepex.commerce.servicenow.incidents;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * An incident's work notes and comments, read from the incident's own {@code work_notes} and {@code comments} fields as
 * ServiceNow shows them: newest entry first, each starting with a line {@code <time> - <who> (<kind>)}, with times in
 * the integration user's time zone. The journal table, {@code sys_journal_field}, is not used: a user with only the
 * {@code itil} role cannot read its rows, and ServiceNow then returns none rather than an error.
 */
final class IncidentJournal {

    /** The fields to request, with {@code sysparm_display_value=all}; {@code sys_updated_on} gives the time zone. */
    static final String FIELDS = "work_notes,comments,sys_updated_on";

    private static final List<String> JOURNAL_FIELDS = List.of("work_notes", "comments");
    private static final DateTimeFormatter SERVICENOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern HEADER = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) - (.+) \\(([^()]+)\\)$");

    private IncidentJournal() {
    }

    /**
     * The entries of an incident row read with {@link #FIELDS}, oldest first. Times are converted to UTC by the
     * difference between {@code sys_updated_on}'s shown and stored value; an entry whose time cannot be read has none.
     */
    static List<ServiceNowClient.JournalEntry> entriesOf(JsonNode incident) {
        Duration toUtc = offsetToUtc(incident.path("sys_updated_on"));
        List<ServiceNowClient.JournalEntry> entries = new ArrayList<>();
        for (String field : JOURNAL_FIELDS) {
            entries.addAll(parse(incident.path(field).path("display_value").asString(""), field, toUtc).reversed());
        }
        entries.sort(Comparator.comparing(ServiceNowClient.JournalEntry::at,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return entries;
    }

    /** The entries of one journal field, newest first as shown. Text before the first header is one entry of its own. */
    private static List<ServiceNowClient.JournalEntry> parse(String shown, String field, Duration toUtc) {
        List<ServiceNowClient.JournalEntry> entries = new ArrayList<>();
        Matcher header = null;
        StringBuilder text = new StringBuilder();
        for (String line : shown.split("\n", -1)) {
            Matcher next = HEADER.matcher(line);
            if (next.matches()) {
                add(entries, header, text, field, toUtc);
                header = next;
                text.setLength(0);
            } else {
                text.append(line).append('\n');
            }
        }
        add(entries, header, text, field, toUtc);
        return entries;
    }

    private static void add(List<ServiceNowClient.JournalEntry> entries, Matcher header, StringBuilder text,
            String field, Duration toUtc) {
        String body = text.toString().strip();
        if (header == null && body.isEmpty()) {
            return;
        }
        entries.add(new ServiceNowClient.JournalEntry(header == null ? null : utc(header.group(1), toUtc),
                header == null ? "" : header.group(2), field, body));
    }

    private static Instant utc(String shown, Duration toUtc) {
        LocalDateTime local = parseTime(shown);
        return local == null || toUtc == null ? null : local.plus(toUtc).toInstant(ZoneOffset.UTC);
    }

    /** Stored minus shown time of the same field: what turns a time shown to the integration user into UTC. */
    private static Duration offsetToUtc(JsonNode updatedOn) {
        LocalDateTime stored = parseTime(updatedOn.path("value").asString(""));
        LocalDateTime shown = parseTime(updatedOn.path("display_value").asString(""));
        return stored == null || shown == null ? null : Duration.between(shown, stored);
    }

    private static LocalDateTime parseTime(String time) {
        try {
            return LocalDateTime.parse(time, SERVICENOW_TIME);
        } catch (DateTimeParseException unreadable) {
            return null;
        }
    }
}
