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
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * The {@link IncidentSystem} on ServiceNow's Table API, called as the integration user. Fields are read with
 * {@code sysparm_display_value=all}, so each comes with its stored value (a state code, a sys_id, a UTC time) and the
 * name a person sees.
 */
@Component
class ServiceNowClient implements IncidentSystem {

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

    @Override
    public Optional<Incident> findByNumber(String number) {
        return query(IncidentQueries.BY_NUMBER.formatted(number), 1).stream().findFirst();
    }

    @Override
    public Optional<Incident> findByCaseId(UUID caseId) {
        return query(IncidentQueries.BY_CASE.formatted(caseId), 1).stream().findFirst();
    }

    @Override
    public Incident create(Map<String, String> fields) {
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

    @Override
    public String linkTo(Incident incident) {
        return linkPrefix() + incident.sysId();
    }

    /** Read by the sys_id that ends the link. */
    @Override
    public Optional<Incident> findLinked(String link) {
        return sysIdOf(link).flatMap(sysId -> query(IncidentQueries.BY_SYS_ID.formatted(sysId), 1).stream().findFirst());
    }

    /** One request per hundred links, instead of one per link: ServiceNow limits how often an integration may call. */
    @Override
    public Map<String, Incident> findAllLinked(List<String> links) {
        var linkBySysId = new LinkedHashMap<String, String>();
        for (var link : links) {
            sysIdOf(link).ifPresent(sysId -> linkBySysId.put(sysId, link));
        }
        var sysIds = List.copyOf(linkBySysId.keySet());
        var found = new HashMap<String, Incident>();
        for (var from = 0; from < sysIds.size(); from += PAGE_SIZE) {
            var page = sysIds.subList(from, Math.min(from + PAGE_SIZE, sysIds.size()));
            var query = IncidentQueries.BY_SYS_IDS.formatted(String.join(IncidentQueries.LIST_SEPARATOR, page));
            for (var incident : query(query, page.size())) {
                var link = linkBySysId.get(incident.sysId());
                if (link != null) {
                    found.put(link, incident);
                }
            }
        }
        return found;
    }

    /** The incident's sys_id in a link to this instance; none for another instance's link or anything else. */
    private Optional<String> sysIdOf(String link) {
        if (link == null || !link.startsWith(linkPrefix())) {
            return Optional.empty();
        }
        var sysId = link.substring(linkPrefix().length());
        // Only a plain id goes into a query: anything else in a link could add terms of its own.
        return SYS_ID.matcher(sysId).matches() ? Optional.of(sysId) : Optional.empty();
    }

    private String linkPrefix() {
        return properties.instanceUrl().replaceAll(Patterns.TRAILING_SLASHES, "") + TableApi.INCIDENT_LINK;
    }

    @Override
    public List<Incident> findNewForAgent() {
        return query(IncidentQueries.NEW_IN_GROUP.formatted(properties.agentGroup()), NEW_INCIDENTS_PER_POLL);
    }

    /** Read a page at a time. */
    @Override
    public List<Incident> findOpenWithCorrelationId() {
        var all = new ArrayList<Incident>();
        List<Incident> page;
        do {
            page = query(IncidentQueries.OPEN_WITH_CORRELATION_ID, PAGE_SIZE, all.size());
            all.addAll(page);
        } while (page.size() == PAGE_SIZE);
        return all;
    }

    @Override
    public List<Incident> findClaimedByAgent() {
        var claimed = IncidentQueries.IN_PROGRESS_IN_GROUP_WITH.formatted(properties.agentGroup(), integrationUserSysId());
        return query(claimed, CLAIMED_INCIDENTS_PER_POLL);
    }

    /**
     * Read from the incident's own journal fields and passed on as shown. The journal table, {@code sys_journal_field},
     * is not used: a user with only the {@code itil} role cannot read its rows, and ServiceNow then returns none rather
     * than an error. The text is not split into entries: a note can contain a line that looks like another entry's
     * heading.
     */
    @Override
    public Journal journalOf(String incidentSysId) {
        return TableApiRows.journalOf(journalFieldsOf(incidentSysId));
    }

    @Override
    public String allWorkNotesOf(String incidentSysId) {
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

    @Override
    public void update(String sysId, Map<String, String> fields) {
        patch(sysId, fields, false);
    }

    @Override
    public void updateByDisplayValue(String sysId, Map<String, String> fields) {
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

    /** Looked up once. */
    @Override
    public String integrationUserSysId() {
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
