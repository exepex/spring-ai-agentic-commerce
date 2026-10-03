package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter SERVICENOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String INCIDENT_FIELDS = "sys_id,number,short_description,description,state,assignment_group,"
            + "assigned_to,caller_id,correlation_id,correlation_display,sys_created_on,sys_updated_on";
    /**
     * How much of each journal field is kept, newest first: enough for every note of a working incident, while a
     * long-lived incident's history cannot flood the agent.
     */
    private static final int MAX_JOURNAL_LENGTH = 20_000;
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
                        .queryParam("sysparm_fields", INCIDENT_FIELDS)
                        .queryParam("sysparm_display_value", "all")
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fields)
                .retrieve().body(JsonNode.class);
        return incidentOf(body.path("result"));
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
        JsonNode incident = journalFieldsOf(incidentSysId);
        return new Journal(newest(incident.path("work_notes").asString("")),
                newest(incident.path("comments").asString("")));
    }

    /** All of the incident's work notes as shown, not cut like {@link #journalOf}: for finding what was sent before. */
    String allWorkNotesOf(String incidentSysId) {
        return journalFieldsOf(incidentSysId).path("work_notes").asString("");
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
        return body.path("result").path(0);
    }

    /** The newest part of a journal field: it is shown newest first, so older entries are cut from the end. */
    private static String newest(String shown) {
        String journal = shown.strip();
        return journal.length() <= MAX_JOURNAL_LENGTH ? journal
                : journal.substring(0, MAX_JOURNAL_LENGTH) + "\n[Older entries left out.]";
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
            String sysId = body.path("result").path(0).path("sys_id").asString("");
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
                        .queryParam("sysparm_fields", INCIDENT_FIELDS)
                        .queryParam("sysparm_display_value", "all")
                        .queryParam("sysparm_limit", limit)
                        .queryParam("sysparm_offset", offset)
                        .build())
                .retrieve().body(JsonNode.class);
        List<Incident> incidents = new ArrayList<>();
        for (JsonNode row : body.path("result")) {
            incidents.add(incidentOf(row));
        }
        return incidents;
    }

    private static Incident incidentOf(JsonNode row) {
        return new Incident(value(row, "sys_id"), value(row, "number"), value(row, "short_description"),
                value(row, "description"), value(row, "state"), display(row, "state"), display(row, "assignment_group"),
                value(row, "assigned_to"), display(row, "assigned_to"), display(row, "caller_id"),
                value(row, "correlation_id").strip(), value(row, "correlation_display").strip(),
                utc(value(row, "sys_created_on")), utc(value(row, "sys_updated_on")));
    }

    private static String value(JsonNode row, String field) {
        return row.path(field).path("value").asString("");
    }

    private static String display(JsonNode row, String field) {
        return row.path(field).path("display_value").asString("");
    }

    /** ServiceNow stores times in UTC as {@code yyyy-MM-dd HH:mm:ss}. */
    private static Instant utc(String serviceNowTime) {
        return serviceNowTime.isBlank() ? null : LocalDateTime.parse(serviceNowTime, SERVICENOW_TIME).toInstant(ZoneOffset.UTC);
    }
}
