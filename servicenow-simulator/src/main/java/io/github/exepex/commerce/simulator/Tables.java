package io.github.exepex.commerce.simulator;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    /**
     * One page of the rows that match an encoded query such as
     * {@code assigned_toISEMPTY^state=1^ORDERBYDESCsys_created_on}.
     */
    synchronized List<Map<String, String>> find(String table, String encodedQuery, int offset, int limit) {
        EncodedQuery query = EncodedQuery.parse(encodedQuery);
        List<Map<String, String>> found = new ArrayList<>();
        for (Map<String, String> row : rows.get(table)) {
            if (query.matches(row, this::valueOf)) {
                found.add(Map.copyOf(row));
            }
        }
        query.order(found);
        int from = Math.min(offset, found.size());
        return found.subList(from, Math.min(from + limit, found.size()));
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
                if (TableFields.isJournalField(field)) {
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

    /**
     * A journal field as a real instance shows it on the incident: newest entry first, each headed by its time, its
     * author's name and its kind. The simulator shows times in UTC.
     */
    synchronized String journalShown(String incidentSysId, String field) {
        String kind = TableFields.journalKind(field);
        StringBuilder shown = new StringBuilder();
        for (Map<String, String> entry : rows.get(JOURNAL).reversed()) {
            if (incidentSysId.equals(entry.get("element_id")) && field.equals(entry.get("element"))) {
                shown.append(entry.get("sys_created_on")).append(" - ").append(nameOf(entry.get("sys_created_by")))
                        .append(" (").append(kind).append(")\n").append(entry.get("value")).append("\n\n");
            }
        }
        return shown.toString();
    }

    private String nameOf(String userName) {
        return rows.get(USER).stream().filter(user -> userName.equals(user.get("user_name")))
                .map(user -> user.get("name")).findFirst().orElse(userName);
    }

    /** What a person sees for a stored value: a state's name, or a referenced row's name. */
    synchronized String displayValue(String table, String field, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (INCIDENT.equals(table) && "state".equals(field)) {
            return TableFields.stateName(value);
        }
        if (TableFields.isReference(table, field)) {
            return get(TableFields.referencedTable(field), value).map(row -> row.get("name")).orElse(value);
        }
        return value;
    }

    /** The stored value for what a person typed: a reference field by its row's name, a state by its name. */
    synchronized String storedValue(String field, String displayed) {
        if (displayed == null || displayed.isBlank()) {
            return "";
        }
        if ("state".equals(field)) {
            return TableFields.stateCode(displayed).orElse(displayed);
        }
        String table = TableFields.referencedTable(field);
        if (table == null) {
            return displayed;
        }
        return rows.get(table).stream()
                .filter(row -> displayed.equals(row.get("name")) || displayed.equals(row.get("user_name")))
                .map(row -> row.get("sys_id"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No " + table + " named '" + displayed + "'"));
    }

    /** A field's stored value; {@code assignment_group.name} follows the reference to the group's name. */
    private String valueOf(Map<String, String> row, String field) {
        int dot = field.indexOf('.');
        if (dot < 0) {
            return row.getOrDefault(field, "");
        }
        String referencedTable = TableFields.referencedTable(field.substring(0, dot));
        if (referencedTable == null) {
            return "";
        }
        return get(referencedTable, row.getOrDefault(field.substring(0, dot), ""))
                .map(referenced -> referenced.getOrDefault(field.substring(dot + 1), ""))
                .orElse("");
    }

    private void addUser(String userName) {
        insert(USER, new LinkedHashMap<>(Map.of("user_name", userName, "name", TableFields.personName(userName))));
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
