package io.github.exepex.commerce.governance.api.client;

import io.github.exepex.commerce.governance.api.GovernancePaths;
import io.github.exepex.commerce.governance.api.dto.AgentDecision;
import io.github.exepex.commerce.governance.api.dto.CaseView;
import io.github.exepex.commerce.governance.api.dto.IncidentLink;
import io.github.exepex.commerce.governance.api.dto.IncidentState;
import io.github.exepex.commerce.governance.api.dto.OutgoingCase;
import io.github.exepex.commerce.governance.api.dto.ServiceDeskIncident;
import io.github.exepex.commerce.governance.api.dto.ToolCallReport;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * An agent reporting to the commerce MCP server, always under its own name: every call carries the agent's
 * {@code Authorization} header (see {@code BearerTokens}), so one agent cannot write in another's name.
 */
public interface AgentGovernanceClient {

    @PostExchange(GovernancePaths.TOOL_CALLS)
    void recordToolCall(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ToolCallReport call);

    @PostExchange(GovernancePaths.DECISIONS)
    void recordDecision(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody AgentDecision decision);

    @GetExchange(GovernancePaths.OUTGOING_CASES)
    List<OutgoingCase> outgoingCases(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostExchange(GovernancePaths.CASE_INCIDENT)
    void linkIncident(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable UUID caseId,
            @RequestBody IncidentLink link);

    @PostExchange(GovernancePaths.CASE_NOTE_SENT)
    void markNoteSent(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable UUID caseId,
            @PathVariable UUID noteId);

    @PostExchange(GovernancePaths.SERVICE_DESK_CASES)
    void recordServiceDeskIncident(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody ServiceDeskIncident incident);

    @GetExchange(GovernancePaths.CASES_IN_SERVICENOW)
    List<CaseView> casesInServiceNow(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostExchange(GovernancePaths.CASE_INCIDENT_STATE)
    void followIncident(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @PathVariable UUID caseId,
            @RequestBody IncidentState state);
}
