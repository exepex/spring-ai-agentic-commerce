package io.github.exepex.commerce.evals;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Plays the service desk and the support teams through ServiceNow's Table API, against the instance the demo uses: the
 * simulator, or a real instance. It is reached at {@code evals.servicenow.url} with the integration user's login, by
 * default from the {@code AGENTIC_COMMERCE_SERVICENOW_*} environment variables the demo reads.
 */
final class ServiceNow {

    private static final Pattern JOURNAL_HEADER = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} - .+ \\([^()]+\\)$");

    private final RestClient api;
    private final String agentGroup;

    ServiceNow() {
        var url = setting("evals.servicenow.url", "AGENTIC_COMMERCE_SERVICENOW_INSTANCE_URL", "http://localhost:8088");
        var username = setting("evals.servicenow.username", "AGENTIC_COMMERCE_SERVICENOW_USERNAME", "trailhead.agent");
        var password = setting("evals.servicenow.password", "AGENTIC_COMMERCE_SERVICENOW_PASSWORD", "simulator");
        this.agentGroup = setting("evals.servicenow.agent-group", "AGENTIC_COMMERCE_SERVICENOW_AGENT_GROUP",
                "Online Shop Agent");
        this.api = RestClient.builder().baseUrl(url)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
                .requestFactory(withTimeouts())
                .build();
    }

    /** The assignment group whose incidents the incident agent works. */
    String agentGroup() {
        return agentGroup;
    }

    /** Raises an incident in the agent's group, as the service desk does, linked to the order; returns its number. */
    String raiseIncident(String shortDescription, String description, String orderId) {
        return api.post().uri("/api/now/table/incident?sysparm_input_display_value=true&sysparm_fields=number")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("assignment_group", agentGroup, "short_description", shortDescription,
                        "description", description, "correlation_id", orderId))
                .retrieve().body(JsonNode.class).path("result").path("number").asString();
    }

    /** The incident's state, by its name. */
    String stateOf(String number) {
        return incident(number).path("state").path("display_value").asString();
    }

    /** The name of the group the incident is assigned to. */
    String assignmentGroupOf(String number) {
        return incident(number).path("assignment_group").path("display_value").asString("");
    }

    /**
     * The text of the incident's work notes, newest first, read from the incident's own work_notes field as ServiceNow
     * shows it: each entry starts with a line {@code <time> - <who> (Work notes)}. The journal table is not readable
     * by the integration user.
     */
    List<String> workNotesOf(String number) {
        var shown = api.get().uri("/api/now/table/incident?sysparm_query=number={number}"
                        + "&sysparm_fields=work_notes&sysparm_display_value=true", number)
                .retrieve().body(JsonNode.class).path("result").path(0).path("work_notes").asString("");
        var notes = new ArrayList<String>();
        StringBuilder note = null;
        for (var line : shown.split("\n", -1)) {
            if (JOURNAL_HEADER.matcher(line).matches()) {
                if (note != null) {
                    notes.add(note.toString().strip());
                }
                note = new StringBuilder();
            } else if (note != null) {
                note.append(line).append('\n');
            }
        }
        if (note != null) {
            notes.add(note.toString().strip());
        }
        return notes;
    }

    /** The incident's fields, each with its stored and its display value. */
    private JsonNode incident(String number) {
        return api.get().uri("/api/now/table/incident?sysparm_query=number={number}"
                        + "&sysparm_fields=sys_id,number,state,assignment_group&sysparm_display_value=all", number)
                .retrieve().body(JsonNode.class).path("result").path(0);
    }

    /** Resolves the incident as the team that has it would. */
    void resolveAsTeam(String number, String resolution) {
        api.patch().uri("/api/now/table/incident/{sysId}", incident(number).path("sys_id").path("value").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("state", "6", "close_code", "Solution provided", "close_notes", resolution))
                .retrieve().toBodilessEntity();
    }

    /** An instance that stops answering fails the scenario instead of blocking it forever. */
    private static JdkClientHttpRequestFactory withTimeouts() {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return requestFactory;
    }

    private static String setting(String property, String environmentVariable, String fallback) {
        var value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
