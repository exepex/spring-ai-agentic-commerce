package io.github.exepex.commerce.simulator;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * The simulated instance's tables, kept in memory: incidents, their work notes and comments, users and groups. Each
 * row holds stored values (state codes, sys_ids, UTC times); a reference field is shown by the referenced row's name.
 * Every method is synchronized, so concurrent calls see whole changes.
 */
@Component
class Tables {

    static final String INCIDENT = "incident";
    static final String JOURNAL = "sys_journal_field";
    static final String USER = "sys_user";
    static final String GROUP = "sys_user_group";

    private static final DateTimeFormatter SERVICENOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    /** The fields that point at another table's row, and that table. */
    private static final Map<String, String> REFERENCES = Map.of("assignment_group", GROUP, "assigned_to", USER,
            "caller_id", USER);
    private static final Map<String, String> STATE_NAMES = Map.of("1", "New", "2", "In Progress", "3", "On Hold",
            "6", "Resolved", "7", "Closed", "8", "Canceled");
    /** Text fields that add a journal entry rather than being stored on the incident. */
    private static final List<String> JOURNAL_FIELDS = List.of("work_notes", "comments");

    private final Map<String, List<Map<String, String>>> rows = new LinkedHashMap<>();
    private final Clock clock;
    private int lastIncidentNumber = 10000;

    Tables(SimulatorProperties properties, Clock clock) {
        this.clock = clock;
        List.of(INCIDENT, JOURNAL, USER, GROUP).forEach(table -> rows.put(table, new ArrayList<>()));
        addUser(properties.username());
        for (SimulatorProperties.Group group : properties.groups()) {
            insert(GROUP, new LinkedHashMap<>(Map.of("name", group.name())));
            group.people().forEach(this::addUser);
        }
    }

    boolean exists(String table) {
        return rows.containsKey(table);
    }

    /** The rows that match an encoded query such as {@code assigned_toISEMPTY^state=1^ORDERBYDESCsys_created_on}. */
    synchronized List<Map<String, String>> find(String table, String encodedQuery, int limit) {
        List<Map<String, String>> found = new ArrayList<>();
        Predicate<Map<String, String>> matches = row -> true;
        String orderBy = null;
        boolean descending = false;
        for (String term : encodedQuery == null || encodedQuery.isBlank() ? new String[0] : encodedQuery.split("\\^")) {
            if (term.startsWith("ORDERBYDESC")) {
                orderBy = term.substring("ORDERBYDESC".length());
                descending = true;
            } else if (term.startsWith("ORDERBY")) {
                orderBy = term.substring("ORDERBY".length());
            } else {
                matches = matches.and(condition(term));
            }
        }
        for (Map<String, String> row : rows.get(table)) {
            if (matches.test(row)) {
                found.add(Map.copyOf(row));
            }
        }
        if (orderBy != null) {
            String field = orderBy;
            // Sorted oldest first, keeping the order rows were added in for equal times, then turned round if asked.
            found.sort(Comparator.comparing(row -> row.getOrDefault(field, "")));
            if (descending) {
                Collections.reverse(found);
            }
        }
        return found.subList(0, Math.min(limit, found.size()));
    }

    synchronized Optional<Map<String, String>> get(String table, String sysId) {
        return rows.get(table).stream().filter(row -> sysId.equals(row.get("sys_id"))).findFirst().map(Map::copyOf);
    }

    /** Opens an incident with the next number. */
    synchronized Map<String, String> createIncident(Map<String, String> fields, String createdBy) {
        Map<String, String> incident = new LinkedHashMap<>();
        incident.put("number", "INC" + String.format(Locale.ROOT, "%07d", ++lastIncidentNumber));
        incident.put("state", "1");
        incident.put("sys_created_by", createdBy);
        insert(INCIDENT, incident);
        return update(incident.get("sys_id"), fields, createdBy).orElseThrow();
    }

    /** Sets the given stored values; work notes and comments become journal entries. Empty if there is no such incident. */
    synchronized Optional<Map<String, String>> update(String sysId, Map<String, String> fields, String changedBy) {
        Optional<Map<String, String>> found = rows.get(INCIDENT).stream()
                .filter(row -> sysId.equals(row.get("sys_id"))).findFirst();
        found.ifPresent(incident -> {
            fields.forEach((field, value) -> {
                if (JOURNAL_FIELDS.contains(field)) {
                    if (value != null && !value.isBlank()) {
                        insert(JOURNAL, new LinkedHashMap<>(Map.of("element_id", sysId, "element", field, "value", value,
                                "sys_created_by", changedBy)));
                    }
                } else {
                    incident.put(field, value == null ? "" : value);
                }
            });
            incident.put("sys_updated_on", now());
        });
        return found.map(Map::copyOf);
    }

    /** What a person sees for a stored value: a state's name, or a referenced row's name. */
    synchronized String displayValue(String table, String field, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (INCIDENT.equals(table) && "state".equals(field)) {
            return STATE_NAMES.getOrDefault(value, value);
        }
        if (INCIDENT.equals(table) && REFERENCES.containsKey(field)) {
            return get(REFERENCES.get(field), value).map(row -> row.get("name")).orElse(value);
        }
        return value;
    }

    /** The stored value for what a person typed: a reference field by its row's name, a state by its name. */
    synchronized String storedValue(String field, String displayed) {
        if (displayed == null || displayed.isBlank()) {
            return "";
        }
        if ("state".equals(field)) {
            return STATE_NAMES.entrySet().stream().filter(state -> state.getValue().equalsIgnoreCase(displayed))
                    .map(Map.Entry::getKey).findFirst().orElse(displayed);
        }
        String table = REFERENCES.get(field);
        if (table == null) {
            return displayed;
        }
        return rows.get(table).stream()
                .filter(row -> displayed.equals(row.get("name")) || displayed.equals(row.get("user_name")))
                .map(row -> row.get("sys_id"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No " + table + " named '" + displayed + "'"));
    }

    private Predicate<Map<String, String>> condition(String term) {
        if (term.endsWith("ISEMPTY")) {
            String field = term.substring(0, term.length() - "ISEMPTY".length());
            return row -> valueOf(row, field).isEmpty();
        }
        int equals = term.indexOf('=');
        if (equals < 0) {
            throw new IllegalArgumentException("The simulator does not understand the query term '" + term + "'");
        }
        String field = term.substring(0, equals);
        String expected = term.substring(equals + 1);
        return row -> expected.equals(valueOf(row, field));
    }

    /** A field's stored value; {@code assignment_group.name} follows the reference to the group's name. */
    private String valueOf(Map<String, String> row, String field) {
        int dot = field.indexOf('.');
        if (dot < 0) {
            return row.getOrDefault(field, "");
        }
        String referencedTable = REFERENCES.get(field.substring(0, dot));
        if (referencedTable == null) {
            return "";
        }
        return get(referencedTable, row.getOrDefault(field.substring(0, dot), ""))
                .map(referenced -> referenced.getOrDefault(field.substring(dot + 1), ""))
                .orElse("");
    }

    private void addUser(String userName) {
        String name = Arrays.stream(userName.split("[._]"))
                .map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((first, second) -> first + " " + second)
                .orElse(userName);
        insert(USER, new LinkedHashMap<>(Map.of("user_name", userName, "name", name)));
    }

    private void insert(String table, Map<String, String> row) {
        row.put("sys_id", UUID.randomUUID().toString().replace("-", ""));
        row.put("sys_created_on", now());
        row.put("sys_updated_on", row.get("sys_created_on"));
        rows.get(table).add(row);
    }

    private String now() {
        return SERVICENOW_TIME.format(clock.instant());
    }
}
