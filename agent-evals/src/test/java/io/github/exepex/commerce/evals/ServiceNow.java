package io.github.exepex.commerce.evals;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Plays the service desk and the support teams through ServiceNow's Table API, against the instance the demo uses: the
 * simulator, or a real instance. It is reached at {@code evals.servicenow.url} with the integration user's login, by
 * default from the {@code AGENTIC_COMMERCE_SERVICENOW_*} environment variables the demo reads.
 */
final class ServiceNow {

    private final RestClient api;
    private final String agentGroup;

    ServiceNow() {
        String url = setting("evals.servicenow.url", "AGENTIC_COMMERCE_SERVICENOW_INSTANCE_URL", "http://localhost:8088");
        String username = setting("evals.servicenow.username", "AGENTIC_COMMERCE_SERVICENOW_USERNAME", "trailhead.agent");
        String password = setting("evals.servicenow.password", "AGENTIC_COMMERCE_SERVICENOW_PASSWORD", "simulator");
        this.agentGroup = setting("evals.servicenow.agent-group", "AGENTIC_COMMERCE_SERVICENOW_AGENT_GROUP",
                "Online Shop Agent");
        this.api = RestClient.builder().baseUrl(url)
                .defaultHeaders(headers -> headers.setBasicAuth(username, password))
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

    /** The incident's state and assignment group, as names. */
    JsonNode incident(String number) {
        return api.get().uri("/api/now/table/incident?sysparm_query=number={number}"
                        + "&sysparm_fields=sys_id,number,state,assignment_group,close_notes&sysparm_display_value=true",
                        number)
                .retrieve().body(JsonNode.class).path("result").path(0);
    }

    /** Resolves the incident as the team that has it would. */
    void resolveAsTeam(String number, String resolution) {
        api.patch().uri("/api/now/table/incident/{sysId}", incident(number).path("sys_id").asString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("state", "6", "close_code", "Solution provided", "close_notes", resolution))
                .retrieve().toBodilessEntity();
    }

    private static String setting(String property, String environmentVariable, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
