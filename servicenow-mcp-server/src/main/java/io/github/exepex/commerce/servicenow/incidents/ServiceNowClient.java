package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Talks to ServiceNow's Table API as the integration user. Fields are read with {@code sysparm_display_value=all}, so
 * each comes with its stored value (a state code, a sys_id, a UTC time) and the name a person sees.
 */
@Component
class ServiceNowClient {

    static final String STATE_NEW = "1";
    static final String STATE_IN_PROGRESS = "2";
    static final String STATE_RESOLVED = "6";
    /** Resolved, closed and cancelled: the incident needs nothing more. */
    static final Set<String> STATES_FINISHED = Set.of(STATE_RESOLVED, "7", "8");
    /** Closed and cancelled: unlike a resolved incident, it can no longer be reopened. */
    static final Set<String> STATES_FINAL = Set.of("7", "8");

    private static final int PAGE_SIZE = 100;
    private static final Pattern SYS_ID = Pattern.compile("[A-Za-z0-9-]+");

    private final ServiceNowProperties properties;
    private final RestClient restClient;
    private volatile String integrationUserSysId;

    ServiceNowClient(ServiceNowProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.clone()
                .baseUrl(properties.isConfigured() ? properties.instanceUrl() : "http://servicenow.invalid")
                .defaultHeaders(headers -> {
                    if (properties.isConfigured()) {
                        headers.setBasicAuth(properties.username(), properties.password());
                    }
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }

    /**
     * An incident as the tools and the poller see it. {@code orderId} is the shop order it is about, taken from its
     * Correlation ID field; empty when it names none. {@code caseId} is the shop's case it was opened for, from its
     * Correlation display field; empty for an incident the service desk raised.
     */
    record Incident(String sysId, String number, String shortDescription, String description, String state,
            String stateName, String assignmentGroup, String assignedToSysId, String assignedTo, String caller,
            String orderId, String caseId, Instant openedAt, Instant updatedAt) {

        boolean isAssigned() {
            return assignedToSysId != null && !assignedToSysId.isBlank();
        }

        /** Resolved, closed or cancelled: the incident needs nothing more. */
        boolean isFinished() {
            return STATES_FINISHED.contains(state);
        }

        /** Closed or cancelled: unlike a resolved incident, it can no longer be reopened. */
        boolean isFinal() {
            return STATES_FINAL.contains(state);
        }

        /**
         * Still the agent's claim: assigned to the integration user, in progress, and in the agent's group. A person who
         * moved the incident to another group has it, even if they left it assigned to the agent.
         */
        boolean isClaimedBy(String integrationUserSysId, String agentGroup) {
            return integrationUserSysId.equals(assignedToSysId) && STATE_IN_PROGRESS.equals(state)
                    && agentGroup.equals(assignmentGroup);
        }

        /** The order the incident names now, in its Correlation ID; null when it names none. */
        UUID linkedOrder() {
            return TableApiRows.idOrNull(orderId);
        }

        /** The case the incident was opened for; null for an incident the service desk raised. */
        UUID openedForCase() {
            return TableApiRows.idOrNull(caseId);
        }
    }

    /**
     * An incident's work notes and comments as ServiceNow shows them: newest entry first, each starting with a line
     * {@code <time> - <who> (<kind>)}, the time in the integration user's time zone.
     */
    record Journal(String workNotes, String comments) {}

    Optional<Incident> findByNumber(String number) {
        return query("number=" + number, 1).stream().findFirst();
    }

    /** The incident opened for a shop case, if one was, so a case never gets two. */
    Optional<Incident> findByCaseId(UUID caseId) {
        return query("correlation_display=" + caseId, 1).stream().findFirst();
    }

    /**
     * Opens an incident. Fields are given as a person sees them, such as the assignment group by its name.
     *
     * @return the incident as created
     */
    Incident create(Map<String, String> fields) {
        JsonNode body = restClient.post()
                .uri(uri -> uri.path("/api/now/table/incident")
                        .queryParam("sysparm_input_display_value", true)
                        .queryParam("sysparm_fields", TableApiRows.INCIDENT_FIELDS)
                        .queryParam("sysparm_display_value", "all")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fields)
                .retrieve().body(JsonNode.class);
        return TableApiRows.incidentOf(body.path("result"));
    }

    /** Where a person opens the incident in ServiceNow. */
    String linkTo(Incident incident) {
        return linkPrefix() + incident.sysId();
    }

    /**
     * The incident a link from {@link #linkTo} points at, read by its sys_id. A link to another instance, such as one an
     * earlier simulator run handed out, points at none of this instance's incidents, whatever its number: numbers
     * repeat across instances.
     */
    Optional<Incident> findLinked(String link) {
        if (link == null || !link.startsWith(linkPrefix())) {
            return Optional.empty();
        }
        String sysId = link.substring(linkPrefix().length());
        // Only a plain id goes into the query: anything else in a link could add terms of its own.
        if (!SYS_ID.matcher(sysId).matches()) {
            return Optional.empty();
        }
        return query("sys_id=" + sysId, 1).stream().findFirst();
    }

    private String linkPrefix() {
        return properties.instanceUrl().replaceAll("/+$", "") + "/incident.do?sys_id=";
    }

    /** New incidents in the agent's group that nobody has taken yet. */
    List<Incident> findNewForAgent() {
        return query("assignment_group.name=" + properties.agentGroup() + "^assigned_toISEMPTY^state=" + STATE_NEW, 20);
    }

    /**
     * Open incidents that name something in their Correlation ID, such as an order: the shop's own and the service
     * desk's. All of them, read a page at a time, oldest first.
     */
    List<Incident> findOpenWithCorrelationId() {
        String openWithCorrelationId = "correlation_idISNOTEMPTY^stateNOT IN"
                + String.join(",", new TreeSet<>(STATES_FINISHED)) + "^ORDERBYsys_created_on";
        List<Incident> all = new ArrayList<>();
        List<Incident> page;
        do {
            page = query(openWithCorrelationId, PAGE_SIZE, all.size());
            all.addAll(page);
        } while (page.size() == PAGE_SIZE);
        return all;
    }

    /** Incidents the agent has claimed and not finished: still in its group, assigned to it and in progress. */
    List<Incident> findClaimedByAgent() {
        return query("assignment_group.name=" + properties.agentGroup() + "^assigned_to=" + integrationUserSysId()
                + "^state=" + STATE_IN_PROGRESS, 50);
    }

    /**
     * The incident's work notes and comments, read from the incident's own journal fields and passed on as shown. The
     * journal table, {@code sys_journal_field}, is not used: a user with only the {@code itil} role cannot read its
     * rows, and ServiceNow then returns none rather than an error. The text is not split into entries: a note can
     * contain a line that looks like another entry's heading.
     */
    Journal journalOf(String incidentSysId) {
        return TableApiRows.journalOf(journalFieldsOf(incidentSysId));
    }

    /** All of the incident's work notes as shown, not cut like {@link #journalOf}: for finding what was sent before. */
    String allWorkNotesOf(String incidentSysId) {
        return TableApiRows.allWorkNotesOf(journalFieldsOf(incidentSysId));
    }

    private JsonNode journalFieldsOf(String incidentSysId) {
        JsonNode body = restClient.get()
                .uri(uri -> uri.path("/api/now/table/incident")
                        .queryParam("sysparm_query", "sys_id=" + incidentSysId)
                        .queryParam("sysparm_fields", "work_notes,comments")
                        .queryParam("sysparm_display_value", true)
                        .queryParam("sysparm_limit", 1)
                        .build())
                .retrieve().body(JsonNode.class);
        return TableApiRows.firstRowOf(body);
    }

    /** Updates fields of an incident with their stored values: state codes and sys_ids. */
    void update(String sysId, Map<String, String> fields) {
        patch(sysId, fields, false);
    }

    /** Updates fields of an incident given as a person sees them, such as an assignment group by its name. */
    void updateByDisplayValue(String sysId, Map<String, String> fields) {
        patch(sysId, fields, true);
    }

    private void patch(String sysId, Map<String, String> fields, boolean displayValues) {
        restClient.patch()
                .uri(uri -> uri.path("/api/now/table/incident/{sysId}")
                        .queryParam("sysparm_input_display_value", displayValues)
                        .build(sysId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(fields)
                .retrieve()
                .toBodilessEntity();
    }

    /** The integration user's sys_id, looked up once: incidents the agent claims are assigned to it. */
    String integrationUserSysId() {
        if (integrationUserSysId == null) {
            JsonNode body = restClient.get()
                    .uri(uri -> uri.path("/api/now/table/sys_user")
                            .queryParam("sysparm_query", "user_name=" + properties.username())
                            .queryParam("sysparm_fields", "sys_id")
                            .queryParam("sysparm_limit", 1)
                            .build())
                    .retrieve().body(JsonNode.class);
            String sysId = TableApiRows.firstRowOf(body).path("sys_id").asString("");
            if (sysId.isBlank()) {
                throw new IllegalStateException("ServiceNow has no user " + properties.username());
            }
            integrationUserSysId = sysId;
        }
        return integrationUserSysId;
    }

    private List<Incident> query(String encodedQuery, int limit) {
        return query(encodedQuery, limit, 0);
    }

    private List<Incident> query(String encodedQuery, int limit, int offset) {
        JsonNode body = restClient.get()
                .uri(uri -> uri.path("/api/now/table/incident")
                        .queryParam("sysparm_query", encodedQuery)
                        .queryParam("sysparm_fields", TableApiRows.INCIDENT_FIELDS)
                        .queryParam("sysparm_display_value", "all")
                        .queryParam("sysparm_limit", limit)
                        .queryParam("sysparm_offset", offset)
                        .build())
                .retrieve().body(JsonNode.class);
        return TableApiRows.incidentsOf(body);
    }
}
