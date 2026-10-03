package io.github.exepex.commerce.simulator;

import io.github.exepex.commerce.simulator.constants.FieldNames;
import io.github.exepex.commerce.simulator.constants.IncidentStates;
import io.github.exepex.commerce.simulator.constants.ServiceNowValues;
import io.github.exepex.commerce.simulator.constants.TableNames;
import io.github.exepex.commerce.simulator.exception.NamedRecordNotFoundException;
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

    private static final DateTimeFormatter SERVICENOW_TIME = DateTimeFormatter.ofPattern(ServiceNowValues.TIME_PATTERN)
            .withZone(ZoneOffset.UTC);

    private final Map<String, List<Map<String, String>>> rows = new LinkedHashMap<>();
    private final Clock clock;
    private int lastIncidentNumber = 10000;

    Tables(SimulatorProperties properties, Clock clock) {
        this.clock = clock;
        List.of(TableNames.INCIDENT, TableNames.JOURNAL, TableNames.USER, TableNames.GROUP)
                .forEach(table -> rows.put(table, new ArrayList<>()));
        addUser(properties.username());
        for (var group : properties.groups()) {
            insert(TableNames.GROUP, new LinkedHashMap<>(Map.of(FieldNames.NAME, group.name())));
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
        var query = EncodedQuery.parse(encodedQuery);
        var found = new ArrayList<Map<String, String>>();
        for (var row : rows.get(table)) {
            if (query.matches(row, this::valueOf)) {
                found.add(Map.copyOf(row));
            }
        }
        query.order(found);
        var from = Math.min(offset, found.size());
        return found.subList(from, Math.min(from + limit, found.size()));
    }

    synchronized Optional<Map<String, String>> get(String table, String sysId) {
        return rows.get(table).stream().filter(row -> sysId.equals(row.get(FieldNames.SYS_ID))).findFirst()
                .map(Map::copyOf);
    }

    /** Opens an incident with the next number. */
    synchronized Map<String, String> createIncident(Map<String, String> fields, String createdBy) {
        var incident = new LinkedHashMap<String, String>();
        incident.put(FieldNames.NUMBER, ServiceNowValues.INCIDENT_NUMBER_PREFIX
                + String.format(Locale.ROOT, ServiceNowValues.INCIDENT_NUMBER_DIGITS, ++lastIncidentNumber));
        incident.put(FieldNames.STATE, IncidentStates.NEW);
        incident.put(FieldNames.SYS_CREATED_BY, createdBy);
        insert(TableNames.INCIDENT, incident);
        return apply(incident, fields, createdBy);
    }

    /** Sets the given stored values; work notes and comments become journal entries. Empty if there is no such incident. */
    synchronized Optional<Map<String, String>> update(String sysId, Map<String, String> fields, String changedBy) {
        return rows.get(TableNames.INCIDENT).stream()
                .filter(row -> sysId.equals(row.get(FieldNames.SYS_ID))).findFirst()
                .map(incident -> apply(incident, fields, changedBy));
    }

    /** Sets the stored values on the incident, adding work notes and comments as journal entries, and returns it. */
    private Map<String, String> apply(Map<String, String> incident, Map<String, String> fields, String changedBy) {
        fields.forEach((field, value) -> {
            if (TableFields.isJournalField(field)) {
                if (value != null && !value.isBlank()) {
                    insert(TableNames.JOURNAL, new LinkedHashMap<>(Map.of(FieldNames.ELEMENT_ID,
                            incident.get(FieldNames.SYS_ID), FieldNames.ELEMENT, field, FieldNames.VALUE, value,
                            FieldNames.SYS_CREATED_BY, changedBy)));
                }
            } else {
                incident.put(field, value == null ? "" : value);
            }
        });
        incident.put(FieldNames.SYS_UPDATED_ON, now());
        return Map.copyOf(incident);
    }

    /**
     * A journal field as a real instance shows it on the incident: newest entry first, each headed by its time, its
     * author's name and its kind. The simulator shows times in UTC.
     */
    synchronized String journalShown(String incidentSysId, String field) {
        var kind = TableFields.journalKind(field);
        var shown = new StringBuilder();
        for (var entry : rows.get(TableNames.JOURNAL).reversed()) {
            if (incidentSysId.equals(entry.get(FieldNames.ELEMENT_ID)) && field.equals(entry.get(FieldNames.ELEMENT))) {
                shown.append(ServiceNowValues.JOURNAL_ENTRY.formatted(entry.get(FieldNames.SYS_CREATED_ON),
                        nameOf(entry.get(FieldNames.SYS_CREATED_BY)), kind, entry.get(FieldNames.VALUE)));
            }
        }
        return shown.toString();
    }

    private String nameOf(String userName) {
        return rows.get(TableNames.USER).stream().filter(user -> userName.equals(user.get(FieldNames.USER_NAME)))
                .map(user -> user.get(FieldNames.NAME)).findFirst().orElse(userName);
    }

    /** What a person sees for a stored value: a state's name, or a referenced row's name. */
    synchronized String displayValue(String table, String field, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (TableNames.INCIDENT.equals(table) && FieldNames.STATE.equals(field)) {
            return TableFields.stateName(value);
        }
        if (TableFields.isReference(table, field)) {
            return get(TableFields.referencedTable(field), value).map(row -> row.get(FieldNames.NAME)).orElse(value);
        }
        return value;
    }

    /** The stored value for what a person typed: a reference field by its row's name, a state by its name. */
    synchronized String storedValue(String field, String displayed) {
        if (displayed == null || displayed.isBlank()) {
            return "";
        }
        if (FieldNames.STATE.equals(field)) {
            return TableFields.stateCode(displayed).orElse(displayed);
        }
        var table = TableFields.referencedTable(field);
        if (table == null) {
            return displayed;
        }
        return rows.get(table).stream()
                .filter(row -> displayed.equals(row.get(FieldNames.NAME))
                        || displayed.equals(row.get(FieldNames.USER_NAME)))
                .map(row -> row.get(FieldNames.SYS_ID))
                .findFirst()
                .orElseThrow(() -> new NamedRecordNotFoundException(table, displayed));
    }

    /** A field's stored value; {@code assignment_group.name} follows the reference to the group's name. */
    private String valueOf(Map<String, String> row, String field) {
        var dot = field.indexOf('.');
        if (dot < 0) {
            return row.getOrDefault(field, "");
        }
        var referencedTable = TableFields.referencedTable(field.substring(0, dot));
        if (referencedTable == null) {
            return "";
        }
        return get(referencedTable, row.getOrDefault(field.substring(0, dot), ""))
                .map(referenced -> referenced.getOrDefault(field.substring(dot + 1), ""))
                .orElse("");
    }

    private void addUser(String userName) {
        insert(TableNames.USER, new LinkedHashMap<>(Map.of(FieldNames.USER_NAME, userName, FieldNames.NAME,
                TableFields.personName(userName))));
    }

    private void insert(String table, Map<String, String> row) {
        row.put(FieldNames.SYS_ID, UUID.randomUUID().toString().replace(ServiceNowValues.UUID_DASH, ""));
        row.put(FieldNames.SYS_CREATED_ON, now());
        row.put(FieldNames.SYS_UPDATED_ON, row.get(FieldNames.SYS_CREATED_ON));
        rows.get(table).add(row);
    }

    private String now() {
        return SERVICENOW_TIME.format(clock.instant());
    }
}
