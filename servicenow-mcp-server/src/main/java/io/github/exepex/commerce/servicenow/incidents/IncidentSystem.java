package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.incidents.dto.Incident;
import io.github.exepex.commerce.servicenow.incidents.dto.Journal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where incidents are kept and worked: what the tools, the case sync and the poller need from ServiceNow, as the
 * integration user the agent works as. {@link ServiceNowClient} reaches it through ServiceNow's Table API.
 */
interface IncidentSystem {

    Optional<Incident> findByNumber(String number);

    /** The incident opened for a shop case, if one was, so a case never gets two. */
    Optional<Incident> findByCaseId(UUID caseId);

    /**
     * Opens an incident. Fields are given as a person sees them, such as the assignment group by its name.
     *
     * @return the incident as created
     */
    Incident create(Map<String, String> fields);

    /** Where a person opens the incident in ServiceNow. */
    String linkTo(Incident incident);

    /**
     * The incident a link from {@link #linkTo} points at. A link to another instance, such as one an earlier simulator
     * run handed out, points at none of this instance's incidents, whatever its number: numbers repeat across
     * instances.
     */
    Optional<Incident> findLinked(String link);

    /**
     * The incidents several links point at, by link, read in as few requests as ServiceNow allows. A link to another
     * instance, or to an incident ServiceNow no longer has, has no entry.
     */
    Map<String, Incident> findAllLinked(List<String> links);

    /** New incidents in the agent's group that nobody has taken yet. */
    List<Incident> findNewForAgent();

    /**
     * Open incidents that name something in their Correlation ID, such as an order: the shop's own and the service
     * desk's. All of them, oldest first.
     */
    List<Incident> findOpenWithCorrelationId();

    /** Incidents the agent has claimed and not finished: still in its group, assigned to it and in progress. */
    List<Incident> findClaimedByAgent();

    /** The incident's work notes and comments as ServiceNow shows them, each cut to its newest part. */
    Journal journalOf(String incidentSysId);

    /** All of the incident's work notes as shown, not cut like {@link #journalOf}: for finding what was sent before. */
    String allWorkNotesOf(String incidentSysId);

    /** Updates fields of an incident with their stored values: state codes and sys_ids. */
    void update(String sysId, Map<String, String> fields);

    /** Updates fields of an incident given as a person sees them, such as an assignment group by its name. */
    void updateByDisplayValue(String sysId, Map<String, String> fields);

    /** The integration user's sys_id: incidents the agent claims are assigned to it. */
    String integrationUserSysId();
}
