package io.github.exepex.commerce.servicenow.governance;

import io.github.exepex.commerce.servicenow.constants.ApiPaths;
import io.github.exepex.commerce.servicenow.governance.dto.Case;
import io.github.exepex.commerce.servicenow.governance.dto.IncidentLink;
import io.github.exepex.commerce.servicenow.governance.dto.IncidentState;
import io.github.exepex.commerce.servicenow.governance.dto.OutgoingCase;
import io.github.exepex.commerce.servicenow.governance.dto.ServiceDeskIncident;
import io.github.exepex.commerce.servicenow.governance.dto.ToolCall;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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

    @GetExchange(ApiPaths.AGENT_SWITCHES)
    Map<String, Boolean> switches();

    @PostExchange(ApiPaths.TOOL_CALLS)
    void recordToolCall(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody ToolCall call);

    @GetExchange(ApiPaths.OUTGOING_CASES)
    List<OutgoingCase> outgoingCases(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostExchange(ApiPaths.CASE_INCIDENT)
    void linkIncident(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable UUID caseId,
            @RequestBody IncidentLink link);

    @PostExchange(ApiPaths.NOTE_SENT)
    void markNoteSent(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable UUID caseId,
            @PathVariable UUID noteId);

    @PostExchange(ApiPaths.SERVICE_DESK_CASES)
    void recordServiceDeskIncident(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ServiceDeskIncident incident);

    @GetExchange(ApiPaths.CASES_IN_SERVICENOW)
    List<Case> casesInServiceNow(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostExchange(ApiPaths.CASE_INCIDENT_STATE)
    void followIncident(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable UUID caseId,
            @RequestBody IncidentState state);
}
