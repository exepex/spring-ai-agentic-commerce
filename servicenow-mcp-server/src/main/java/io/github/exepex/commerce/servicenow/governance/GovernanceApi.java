package io.github.exepex.commerce.servicenow.governance;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * The commerce MCP server's governance API: the agents' kill switches, the audit trail they all share, and the shop's
 * cases, which the poller carries to ServiceNow as incidents. Calls that act for an agent carry its bearer token.
 */
public interface GovernanceApi {

    /** One tool call, recorded as the agent that made it; {@code outcome} is SUCCEEDED, FAILED or DENIED. */
    record ToolCall(UUID orderId, String action, String outcome, String summary, String details) {}

    /**
     * A shop case; {@code incidentNumber} and {@code incidentUrl} are empty until its incident is opened. The link, not
     * the number, says which incident it is: numbers repeat across instances. {@code forPeople} means its incident goes
     * straight to the default team, not to the agent; missing means no.
     */
    record Case(UUID id, UUID orderId, String type, String status, String title, String description,
            String incidentNumber, String incidentUrl, String assignmentGroup, Boolean forPeople) {

        public boolean isForPeople() {
            return Boolean.TRUE.equals(forPeople);
        }
    }

    record Note(UUID id, String text) {}

    /** A case with what is still to be sent: its incident, when it has none, and the notes added to it since. */
    record OutgoingCase(Case supportCase, List<Note> unsentNotes) {}

    record IncidentLink(String number, String url) {}

    /** Who has the case's incident now; {@code status} is WITH_AGENT, WITH_TEAM or RESOLVED. */
    record IncidentState(String number, String status, String assignmentGroup) {}

    /** An incident the service desk raised about an order, and who has it; {@code status} is WITH_AGENT or WITH_TEAM. */
    record ServiceDeskIncident(UUID orderId, String number, String url, String shortDescription, String status,
            String assignmentGroup) {}

    @GetExchange("/api/agent-switches")
    Map<String, Boolean> switches();

    @PostExchange("/api/agent/tool-calls")
    void recordToolCall(@RequestHeader("Authorization") String authorization, @RequestBody ToolCall call);

    @GetExchange("/api/agent/cases/outgoing")
    List<OutgoingCase> outgoingCases(@RequestHeader("Authorization") String authorization);

    @PostExchange("/api/agent/cases/{caseId}/incident")
    void linkIncident(@RequestHeader("Authorization") String authorization, @PathVariable UUID caseId,
            @RequestBody IncidentLink link);

    @PostExchange("/api/agent/cases/{caseId}/notes/{noteId}/sent")
    void markNoteSent(@RequestHeader("Authorization") String authorization, @PathVariable UUID caseId,
            @PathVariable UUID noteId);

    @PostExchange("/api/agent/cases/service-desk")
    void recordServiceDeskIncident(@RequestHeader("Authorization") String authorization,
            @RequestBody ServiceDeskIncident incident);

    @GetExchange("/api/agent/cases/in-servicenow")
    List<Case> casesInServiceNow(@RequestHeader("Authorization") String authorization);

    @PostExchange("/api/agent/cases/{caseId}/incident-state")
    void followIncident(@RequestHeader("Authorization") String authorization, @PathVariable UUID caseId,
            @RequestBody IncidentState state);
}
