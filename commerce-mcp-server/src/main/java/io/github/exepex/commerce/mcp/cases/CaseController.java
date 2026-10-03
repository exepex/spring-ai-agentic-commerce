package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.mcp.cases.dto.CaseView;
import io.github.exepex.commerce.mcp.cases.dto.IncidentLink;
import io.github.exepex.commerce.mcp.cases.dto.IncidentState;
import io.github.exepex.commerce.mcp.cases.dto.OutgoingView;
import io.github.exepex.commerce.mcp.cases.dto.ServiceDeskIncident;
import io.github.exepex.commerce.mcp.constants.ApiPaths;
import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import io.github.exepex.commerce.mcp.constants.SecurityValues;
import io.github.exepex.commerce.mcp.exception.NotTheCaseWorkerException;
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

    @GetMapping(ApiPaths.OUTGOING_CASES)
    List<OutgoingView> outgoing(@RequestAttribute(SecurityValues.AGENT_ID_ATTRIBUTE) String agentId) {
        ensureWorker(agentId);
        return cases.toSend().stream().map(CaseMapper::toView).toList();
    }

    @PostMapping(ApiPaths.CASE_INCIDENT)
    CaseView linkIncident(@RequestAttribute(SecurityValues.AGENT_ID_ATTRIBUTE) String agentId,
            @PathVariable UUID caseId, @Valid @RequestBody IncidentLink link) {
        ensureWorker(agentId);
        return CaseMapper.toView(cases.linkIncident(caseId, link.number(), link.url()));
    }

    @PostMapping(ApiPaths.SERVICE_DESK_CASES)
    CaseView recordServiceDeskIncident(@RequestAttribute(SecurityValues.AGENT_ID_ATTRIBUTE) String agentId,
            @Valid @RequestBody ServiceDeskIncident incident) {
        ensureWorker(agentId);
        return CaseMapper.toView(cases.recordServiceDeskIncident(incident.orderId(), incident.number(), incident.url(),
                incident.shortDescription(), incident.status(), incident.assignmentGroup()));
    }

    @PostMapping(ApiPaths.CASE_NOTE_SENT)
    void markNoteSent(@RequestAttribute(SecurityValues.AGENT_ID_ATTRIBUTE) String agentId,
            @PathVariable UUID caseId, @PathVariable UUID noteId) {
        ensureWorker(agentId);
        cases.markNoteSent(caseId, noteId);
    }

    @GetMapping(ApiPaths.CASES_IN_SERVICENOW)
    List<CaseView> inServiceNow(@RequestAttribute(SecurityValues.AGENT_ID_ATTRIBUTE) String agentId) {
        ensureWorker(agentId);
        return cases.inServiceNow().stream().map(CaseMapper::toView).toList();
    }

    @PostMapping(ApiPaths.CASE_INCIDENT_STATE)
    CaseView followIncident(@RequestAttribute(SecurityValues.AGENT_ID_ATTRIBUTE) String agentId,
            @PathVariable UUID caseId, @Valid @RequestBody IncidentState state) {
        ensureWorker(agentId);
        return CaseMapper.toView(cases.followIncident(caseId, state.number(), state.status(), state.assignmentGroup(),
                state.incidentFinal(), state.orderId()));
    }

    private void ensureWorker(String agentId) {
        if (!worker.equals(agentId)) {
            throw new NotTheCaseWorkerException(worker);
        }
    }
}
