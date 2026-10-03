package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.mcp.cases.CaseViews.CaseView;
import io.github.exepex.commerce.mcp.cases.CaseViews.OutgoingView;
import io.github.exepex.commerce.mcp.governance.GovernanceException;
import io.github.exepex.commerce.mcp.security.AgentAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

    record IncidentLink(@NotBlank @Size(max = 40) String number, @Size(max = 500) String url) {}

    /**
     * Who has a case's incident now. {@code number} is the incident the state was read from; it must be the case's own.
     * {@code incidentFinal} means a resolved one is closed or cancelled, and {@code orderId} is the order the incident
     * names now, if any.
     */
    record IncidentState(@NotBlank @Size(max = 40) String number, @NotNull SupportCase.Status status,
            @Size(max = 200) String assignmentGroup, boolean incidentFinal, UUID orderId) {}

    /** An incident the service desk raised about an order, as the poller found it; see {@link CaseType#SERVICE_DESK}. */
    record ServiceDeskIncident(@NotNull UUID orderId, @NotBlank @Size(max = 40) String number,
            @NotBlank @Size(max = 500) String url, @NotBlank @Size(max = 4000) String shortDescription,
            @NotNull SupportCase.Status status, @Size(max = 200) String assignmentGroup) {}

    private final CaseService cases;

    @Value("${commerce.cases.worker}")
    private final String worker;

    /** Unresolved cases with {@code open=true}, an order's cases with {@code orderId}, otherwise the 100 most recent. */
    @GetMapping("/api/cases")
    List<CaseView> cases(@RequestParam(required = false) Boolean open, @RequestParam(required = false) UUID orderId) {
        List<SupportCase> found = orderId != null ? cases.forOrder(orderId)
                : Boolean.TRUE.equals(open) ? cases.unresolved() : cases.recent();
        return found.stream().map(CaseViews::toView).toList();
    }

    @GetMapping("/api/agent/cases/outgoing")
    List<OutgoingView> outgoing(@RequestAttribute(AgentAuthenticationFilter.AGENT_ID_ATTRIBUTE) String agentId) {
        ensureWorker(agentId);
        return cases.toSend().stream().map(CaseViews::toView).toList();
    }

    @PostMapping("/api/agent/cases/{caseId}/incident")
    CaseView linkIncident(@RequestAttribute(AgentAuthenticationFilter.AGENT_ID_ATTRIBUTE) String agentId,
            @PathVariable UUID caseId, @Valid @RequestBody IncidentLink link) {
        ensureWorker(agentId);
        return CaseViews.toView(cases.linkIncident(caseId, link.number(), link.url()));
    }

    @PostMapping("/api/agent/cases/service-desk")
    CaseView recordServiceDeskIncident(@RequestAttribute(AgentAuthenticationFilter.AGENT_ID_ATTRIBUTE) String agentId,
            @Valid @RequestBody ServiceDeskIncident incident) {
        ensureWorker(agentId);
        return CaseViews.toView(cases.recordServiceDeskIncident(incident.orderId(), incident.number(), incident.url(),
                incident.shortDescription(), incident.status(), incident.assignmentGroup()));
    }

    @PostMapping("/api/agent/cases/{caseId}/notes/{noteId}/sent")
    void markNoteSent(@RequestAttribute(AgentAuthenticationFilter.AGENT_ID_ATTRIBUTE) String agentId,
            @PathVariable UUID caseId, @PathVariable UUID noteId) {
        ensureWorker(agentId);
        cases.markNoteSent(caseId, noteId);
    }

    @GetMapping("/api/agent/cases/in-servicenow")
    List<CaseView> inServiceNow(@RequestAttribute(AgentAuthenticationFilter.AGENT_ID_ATTRIBUTE) String agentId) {
        ensureWorker(agentId);
        return cases.inServiceNow().stream().map(CaseViews::toView).toList();
    }

    @PostMapping("/api/agent/cases/{caseId}/incident-state")
    CaseView followIncident(@RequestAttribute(AgentAuthenticationFilter.AGENT_ID_ATTRIBUTE) String agentId,
            @PathVariable UUID caseId, @Valid @RequestBody IncidentState state) {
        ensureWorker(agentId);
        return CaseViews.toView(cases.followIncident(caseId, state.number(), state.status(), state.assignmentGroup(),
                state.incidentFinal(), state.orderId()));
    }

    private void ensureWorker(String agentId) {
        if (!worker.equals(agentId)) {
            throw new GovernanceException(HttpStatus.FORBIDDEN, "Only the " + worker + " syncs cases with ServiceNow");
        }
    }
}
