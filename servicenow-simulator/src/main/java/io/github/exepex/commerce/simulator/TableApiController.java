package io.github.exepex.commerce.simulator;

import io.github.exepex.commerce.simulator.constants.ApiPaths;
import io.github.exepex.commerce.simulator.constants.FieldNames;
import io.github.exepex.commerce.simulator.constants.TableApi;
import io.github.exepex.commerce.simulator.constants.TableNames;
import io.github.exepex.commerce.simulator.exception.IncidentNotFoundException;
import io.github.exepex.commerce.simulator.exception.InvalidTableException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The part of ServiceNow's Table API the demo uses: query any of the simulated tables, and open and update incidents.
 * Rows are returned as ServiceNow returns them: with {@code sysparm_display_value=all} each field is a {@code value}
 * and {@code display_value} pair, with {@code true} its display value, otherwise its stored value; a reference field
 * is always an object that also carries a {@code link} to the referenced row. With {@code sysparm_input_display_value=true},
 * reference fields are given by name, such as an assignment group. Work notes and comments are shown on the incident,
 * as a real instance shows them; their journal rows are not readable, as for an integration user on a real instance.
 */
@RestController
@RequiredArgsConstructor
class TableApiController {

    private final Tables tables;
    private final SimulatorProperties properties;

    @GetMapping(ApiPaths.TABLE)
    Map<String, Object> query(@PathVariable String table,
            @RequestParam(name = TableApi.QUERY, required = false) String query,
            @RequestParam(name = TableApi.FIELDS, required = false) String fields,
            @RequestParam(name = TableApi.DISPLAY_VALUE, defaultValue = TableApi.SHOW_STORED) String displayValue,
            @RequestParam(name = TableApi.LIMIT, defaultValue = TableApi.DEFAULT_LIMIT) int limit,
            @RequestParam(name = TableApi.OFFSET, defaultValue = TableApi.DEFAULT_OFFSET) int offset) {
        if (!tables.exists(table)) {
            throw new InvalidTableException(table);
        }
        if (TableNames.JOURNAL.equals(table)) {
            // A user with only the itil role, like the integration user, may not read journal rows on a real instance,
            // which then answers with none; the notes are read through the incident's own journal fields.
            return Map.of(TableApi.RESULT, List.of());
        }
        return Map.of(TableApi.RESULT, tables.find(table, query, offset, limit).stream()
                .map(row -> render(table, row, fields, displayValue))
                .toList());
    }

    @PostMapping(ApiPaths.INCIDENTS)
    ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body,
            @RequestParam(name = TableApi.INPUT_DISPLAY_VALUE, defaultValue = TableApi.SHOW_STORED)
                    boolean inputDisplayValue,
            @RequestParam(name = TableApi.FIELDS, required = false) String fields,
            @RequestParam(name = TableApi.DISPLAY_VALUE, defaultValue = TableApi.SHOW_STORED) String displayValue) {
        var incident = tables.createIncident(stored(body, inputDisplayValue), properties.username());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(TableApi.RESULT, render(TableNames.INCIDENT, incident, fields, displayValue)));
    }

    @PatchMapping(ApiPaths.INCIDENT)
    Map<String, Object> update(@PathVariable String sysId, @RequestBody Map<String, String> body,
            @RequestParam(name = TableApi.INPUT_DISPLAY_VALUE, defaultValue = TableApi.SHOW_STORED)
                    boolean inputDisplayValue) {
        var incident = tables.update(sysId, stored(body, inputDisplayValue), properties.username())
                .orElseThrow(() -> new IncidentNotFoundException(sysId));
        return Map.of(TableApi.RESULT, render(TableNames.INCIDENT, incident, null, TableApi.SHOW_STORED));
    }

    private Map<String, String> stored(Map<String, String> body, boolean inputDisplayValue) {
        var stored = new LinkedHashMap<String, String>();
        body.forEach((field, value) -> stored.put(field, inputDisplayValue ? tables.storedValue(field, value) : value));
        return stored;
    }

    /** Where the referenced row is, as ServiceNow gives it next to a reference field's value. */
    private String link(String field, String sysId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ApiPaths.RECORD)
                .buildAndExpand(TableFields.referencedTable(field), sysId)
                .toUriString();
    }

    private Map<String, Object> render(String table, Map<String, String> row, String fields, String displayValue) {
        var shown = fields == null || fields.isBlank() ? List.copyOf(row.keySet())
                : Arrays.stream(fields.split(TableApi.FIELD_SEPARATOR)).map(String::strip).toList();
        var rendered = new LinkedHashMap<String, Object>();
        for (var field : shown) {
            var value = row.getOrDefault(field, "");
            var display = TableFields.isJournal(table, field) ? tables.journalShown(row.get(FieldNames.SYS_ID), field)
                    : tables.displayValue(table, field, value);
            var reference = TableFields.isReference(table, field) && !value.isEmpty();
            rendered.put(field, switch (displayValue) {
                case TableApi.SHOW_ALL -> reference
                        ? Map.of(TableApi.VALUE, value, TableApi.SHOWN_VALUE, display, TableApi.LINK, link(field, value))
                        : Map.of(TableApi.VALUE, value, TableApi.SHOWN_VALUE, display);
                case TableApi.SHOW_DISPLAY -> reference
                        ? Map.of(TableApi.SHOWN_VALUE, display, TableApi.LINK, link(field, value)) : display;
                default -> reference ? Map.of(TableApi.VALUE, value, TableApi.LINK, link(field, value)) : value;
            });
        }
        return rendered;
    }
}
