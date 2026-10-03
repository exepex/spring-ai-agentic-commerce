package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.constants.IncidentQueries;
import io.github.exepex.commerce.servicenow.constants.Patterns;
import io.github.exepex.commerce.servicenow.constants.ServiceNowFields;
import io.github.exepex.commerce.servicenow.constants.TableApi;
import io.github.exepex.commerce.servicenow.exception.IntegrationUserNotFoundException;
import io.github.exepex.commerce.servicenow.incidents.dto.Incident;
import io.github.exepex.commerce.servicenow.incidents.dto.Journal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final int PAGE_SIZE = 100;
    private static final int NEW_INCIDENTS_PER_POLL = 20;
    private static final int CLAIMED_INCIDENTS_PER_POLL = 50;
    private static final Pattern SYS_ID = Pattern.compile(Patterns.SYS_ID);

    private final ServiceNowProperties properties;
    private final RestClient restClient;
    private volatile String integrationUserSysId;

    ServiceNowClient(ServiceNowProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.clone()
                .baseUrl(properties.isConfigured() ? properties.instanceUrl() : TableApi.NO_INSTANCE)
                .defaultHeaders(headers -> {
                    if (properties.isConfigured()) {
                        headers.setBasicAuth(properties.username(), properties.password());
                    }
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .build();
    }

    Optional<Incident> findByNumber(String number) {
        return query(IncidentQueries.BY_NUMBER.formatted(number), 1).stream().findFirst();
    }

    /** The incident opened for a shop case, if one was, so a case never gets two. */
    Optional<Incident> findByCaseId(UUID caseId) {
        return query(IncidentQueries.BY_CASE.formatted(caseId), 1).stream().findFirst();
    }

    /**
     * Opens an incident. Fields are given as a person sees them, such as the assignment group by its name.
     *
     * @return the incident as created
     */
    Incident create(Map<String, String> fields) {
        var body = restClient.post()
                .uri(uri -> uri.path(TableApi.INCIDENTS)
                        .queryParam(TableApi.INPUT_DISPLAY_VALUE, true)
                        .queryParam(TableApi.FIELDS, ServiceNowFields.INCIDENT_FIELDS)
                        .queryParam(TableApi.DISPLAY_VALUE, TableApi.SHOW_ALL)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(fields)
                .retrieve().body(JsonNode.class);
        return TableApiRows.incidentOf(body.path(TableApi.RESULT));
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
        var sysId = link.substring(linkPrefix().length());
        // Only a plain id goes into the query: anything else in a link could add terms of its own.
        if (!SYS_ID.matcher(sysId).matches()) {
            return Optional.empty();
        }
        return query(IncidentQueries.BY_SYS_ID.formatted(sysId), 1).stream().findFirst();
    }

    private String linkPrefix() {
        return properties.instanceUrl().replaceAll(Patterns.TRAILING_SLASHES, "") + TableApi.INCIDENT_LINK;
    }

    /** New incidents in the agent's group that nobody has taken yet. */
    List<Incident> findNewForAgent() {
        return query(IncidentQueries.NEW_IN_GROUP.formatted(properties.agentGroup()), NEW_INCIDENTS_PER_POLL);
    }

    /**
     * Open incidents that name something in their Correlation ID, such as an order: the shop's own and the service
     * desk's. All of them, read a page at a time, oldest first.
     */
    List<Incident> findOpenWithCorrelationId() {
        var all = new ArrayList<Incident>();
        List<Incident> page;
        do {
            page = query(IncidentQueries.OPEN_WITH_CORRELATION_ID, PAGE_SIZE, all.size());
            all.addAll(page);
        } while (page.size() == PAGE_SIZE);
        return all;
    }

    /** Incidents the agent has claimed and not finished: still in its group, assigned to it and in progress. */
    List<Incident> findClaimedByAgent() {
        var claimed = IncidentQueries.IN_PROGRESS_IN_GROUP_WITH.formatted(properties.agentGroup(), integrationUserSysId());
        return query(claimed, CLAIMED_INCIDENTS_PER_POLL);
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
        var body = restClient.get()
                .uri(uri -> uri.path(TableApi.INCIDENTS)
                        .queryParam(TableApi.QUERY, IncidentQueries.BY_SYS_ID.formatted(incidentSysId))
                        .queryParam(TableApi.FIELDS, ServiceNowFields.JOURNAL_FIELDS)
                        .queryParam(TableApi.DISPLAY_VALUE, true)
                        .queryParam(TableApi.LIMIT, 1)
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
                .uri(uri -> uri.path(TableApi.INCIDENT)
                        .queryParam(TableApi.INPUT_DISPLAY_VALUE, displayValues)
                        .build(sysId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(fields)
                .retrieve()
                .toBodilessEntity();
    }

    /** The integration user's sys_id, looked up once: incidents the agent claims are assigned to it. */
    String integrationUserSysId() {
        if (integrationUserSysId == null) {
            var body = restClient.get()
                    .uri(uri -> uri.path(TableApi.USERS)
                            .queryParam(TableApi.QUERY, IncidentQueries.USER_BY_NAME.formatted(properties.username()))
                            .queryParam(TableApi.FIELDS, ServiceNowFields.SYS_ID)
                            .queryParam(TableApi.LIMIT, 1)
                            .build())
                    .retrieve().body(JsonNode.class);
            var sysId = TableApiRows.firstRowOf(body).path(ServiceNowFields.SYS_ID).asString("");
            if (sysId.isBlank()) {
                throw new IntegrationUserNotFoundException(properties.username());
            }
            integrationUserSysId = sysId;
        }
        return integrationUserSysId;
    }

    private List<Incident> query(String encodedQuery, int limit) {
        return query(encodedQuery, limit, 0);
    }

    private List<Incident> query(String encodedQuery, int limit, int offset) {
        var body = restClient.get()
                .uri(uri -> uri.path(TableApi.INCIDENTS)
                        .queryParam(TableApi.QUERY, encodedQuery)
                        .queryParam(TableApi.FIELDS, ServiceNowFields.INCIDENT_FIELDS)
                        .queryParam(TableApi.DISPLAY_VALUE, TableApi.SHOW_ALL)
                        .queryParam(TableApi.LIMIT, limit)
                        .queryParam(TableApi.OFFSET, offset)
                        .build())
                .retrieve().body(JsonNode.class);
        return TableApiRows.incidentsOf(body);
    }
}
