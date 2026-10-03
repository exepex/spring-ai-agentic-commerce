package io.github.exepex.commerce.simulator;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
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
class TableApiController {

    private final Tables tables;
    private final SimulatorProperties properties;

    TableApiController(Tables tables, SimulatorProperties properties) {
        this.tables = tables;
        this.properties = properties;
    }

    @GetMapping("/api/now/table/{table}")
    Map<String, Object> query(@PathVariable String table,
            @RequestParam(name = "sysparm_query", required = false) String query,
            @RequestParam(name = "sysparm_fields", required = false) String fields,
            @RequestParam(name = "sysparm_display_value", defaultValue = "false") String displayValue,
            @RequestParam(name = "sysparm_limit", defaultValue = "10000") int limit) {
        if (!tables.exists(table)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid table " + table);
        }
        if (Tables.JOURNAL.equals(table)) {
            // A user with only the itil role, like the integration user, may not read journal rows on a real instance,
            // which then answers with none; the notes are read through the incident's own journal fields.
            return Map.of("result", List.of());
        }
        return Map.of("result", tables.find(table, query, limit).stream()
                .map(row -> render(table, row, fields, displayValue))
                .toList());
    }

    @PostMapping("/api/now/table/incident")
    ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, String> body,
            @RequestParam(name = "sysparm_input_display_value", defaultValue = "false") boolean inputDisplayValue,
            @RequestParam(name = "sysparm_fields", required = false) String fields,
            @RequestParam(name = "sysparm_display_value", defaultValue = "false") String displayValue) {
        Map<String, String> incident = tables.createIncident(stored(body, inputDisplayValue), properties.username());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("result", render(Tables.INCIDENT, incident, fields, displayValue)));
    }

    @PatchMapping("/api/now/table/incident/{sysId}")
    Map<String, Object> update(@PathVariable String sysId, @RequestBody Map<String, String> body,
            @RequestParam(name = "sysparm_input_display_value", defaultValue = "false") boolean inputDisplayValue) {
        Map<String, String> incident = tables.update(sysId, stored(body, inputDisplayValue), properties.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No incident " + sysId));
        return Map.of("result", render(Tables.INCIDENT, incident, null, "false"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> refuse(IllegalArgumentException invalid) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", Map.of("message", invalid.getMessage()), "status", "failure"));
    }

    private Map<String, String> stored(Map<String, String> body, boolean inputDisplayValue) {
        Map<String, String> stored = new LinkedHashMap<>();
        body.forEach((field, value) -> stored.put(field, inputDisplayValue ? tables.storedValue(field, value) : value));
        return stored;
    }

    /** Where the referenced row is, as ServiceNow gives it next to a reference field's value. */
    private String link(String field, String sysId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/now/table/{table}/{sysId}")
                .buildAndExpand(tables.referencedTable(field), sysId)
                .toUriString();
    }

    private Map<String, Object> render(String table, Map<String, String> row, String fields, String displayValue) {
        List<String> shown = fields == null || fields.isBlank() ? List.copyOf(row.keySet())
                : Arrays.stream(fields.split(",")).map(String::strip).toList();
        Map<String, Object> rendered = new LinkedHashMap<>();
        for (String field : shown) {
            String value = row.getOrDefault(field, "");
            String display = tables.isJournal(table, field) ? tables.journalShown(row.get("sys_id"), field)
                    : tables.displayValue(table, field, value);
            boolean reference = tables.isReference(table, field) && !value.isEmpty();
            rendered.put(field, switch (displayValue) {
                case "all" -> reference ? Map.of("value", value, "display_value", display, "link", link(field, value))
                        : Map.of("value", value, "display_value", display);
                case "true" -> reference ? Map.of("display_value", display, "link", link(field, value)) : display;
                default -> reference ? Map.of("value", value, "link", link(field, value)) : value;
            });
        }
        return rendered;
    }
}
