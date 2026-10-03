package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.governance.api.GovernancePaths;
import io.github.exepex.commerce.governance.api.dto.CaseView;
import io.github.exepex.commerce.governance.api.dto.IncidentLink;
import io.github.exepex.commerce.governance.api.dto.IncidentState;
import io.github.exepex.commerce.governance.api.dto.OutgoingCase;
import io.github.exepex.commerce.governance.api.dto.ServiceDeskIncident;
import io.github.exepex.commerce.mcp.constants.ApiPaths;
import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import io.github.exepex.commerce.mcp.exception.NotTheCaseWorkerException;
import io.github.exepex.commerce.mcpserver.security.CallingAgent;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cases for the operations console, and the case sync for the ServiceNow MCP server's poller under
 * {@code /api/agent/cases}. The poller works for the agent named in {@code commerce.cases.worker} and authenticates
 * with that agent's bearer token; no other agent may sync cases.
 */
@RestController
@RequiredArgsConstructor
class CaseController {

    private final CaseService cases;

    @Value(ConfigKeys.CASE_WORKER)
    private final String worker;

    /** Unresolved cases with {@code open=true}, an order's cases with {@code orderId}, otherwise the 100 most recent. */
    @GetMapping(ApiPaths.CASES)
    List<CaseView> cases(@RequestParam(required = false) Boolean open, @RequestParam(required = false) UUID orderId) {
        var found = orderId != null ? cases.forOrder(orderId)
                : Boolean.TRUE.equals(open) ? cases.unresolved() : cases.recent();
        return found.stream().map(CaseMapper::toView).toList();
    }

    @GetMapping(GovernancePaths.OUTGOING_CASES)
    List<OutgoingCase> outgoing(@RequestAttribute(CallingAgent.REQUEST_ATTRIBUTE) String agentId) {
        ensureWorker(agentId);
        return cases.toSend().stream().map(CaseMapper::toView).toList();
    }

    @PostMapping(GovernancePaths.CASE_INCIDENT)
    CaseView linkIncident(@RequestAttribute(CallingAgent.REQUEST_ATTRIBUTE) String agentId,
            @PathVariable UUID caseId, @Valid @RequestBody IncidentLink link) {
        ensureWorker(agentId);
        return CaseMapper.toView(cases.linkIncident(caseId, link.number(), link.url()));
    }

    @PostMapping(GovernancePaths.SERVICE_DESK_CASES)
    CaseView recordServiceDeskIncident(@RequestAttribute(CallingAgent.REQUEST_ATTRIBUTE) String agentId,
            @Valid @RequestBody ServiceDeskIncident incident) {
        ensureWorker(agentId);
        return CaseMapper.toView(cases.recordServiceDeskIncident(incident.orderId(), incident.number(), incident.url(),
                incident.shortDescription(), CaseMapper.toStatus(incident.status()), incident.assignmentGroup()));
    }

    @PostMapping(GovernancePaths.CASE_NOTE_SENT)
    void markNoteSent(@RequestAttribute(CallingAgent.REQUEST_ATTRIBUTE) String agentId,
            @PathVariable UUID caseId, @PathVariable UUID noteId) {
        ensureWorker(agentId);
        cases.markNoteSent(caseId, noteId);
    }

    @GetMapping(GovernancePaths.CASES_IN_SERVICENOW)
    List<CaseView> inServiceNow(@RequestAttribute(CallingAgent.REQUEST_ATTRIBUTE) String agentId) {
        ensureWorker(agentId);
        return cases.inServiceNow().stream().map(CaseMapper::toView).toList();
    }

    @PostMapping(GovernancePaths.CASE_INCIDENT_STATE)
    CaseView followIncident(@RequestAttribute(CallingAgent.REQUEST_ATTRIBUTE) String agentId,
            @PathVariable UUID caseId, @Valid @RequestBody IncidentState state) {
        ensureWorker(agentId);
        return CaseMapper.toView(cases.followIncident(caseId, state.number(), CaseMapper.toStatus(state.status()),
                state.assignmentGroup(), state.incidentFinal(), state.orderId()));
    }

    private void ensureWorker(String agentId) {
        if (!worker.equals(agentId)) {
            throw new NotTheCaseWorkerException(worker);
        }
    }
}
