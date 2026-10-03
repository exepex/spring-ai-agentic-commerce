package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.mcp.constants.Actors;
import io.github.exepex.commerce.mcp.constants.AuditActions;
import io.github.exepex.commerce.mcp.constants.CaseWording;
import io.github.exepex.commerce.mcp.governance.AuditEvent;
import io.github.exepex.commerce.mcp.governance.AuditTrail;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps the cases of incidents the service desk raised about an order in step with those incidents: which order each
 * one is about, and whether it is open. It works within the transaction of the {@link CaseService} call that uses it,
 * so a case and its entries on the timeline are saved together or not at all.
 */
@Component
@RequiredArgsConstructor
class ServiceDeskCases {

    private final SupportCaseRepository cases;
    private final AuditTrail audit;
    private final Clock clock;

    /** Records the incident as a case, or follows the case recorded for it before; see {@link CaseService}. */
    SupportCase record(UUID orderId, String number, String url, String shortDescription, SupportCase.Status status,
            String assignmentGroup) {
        var recorded = cases.findByTypeAndIncidentUrl(CaseType.SERVICE_DESK, url);
        if (recorded.isPresent()) {
            follow(recorded.get(), number, orderId, status, assignmentGroup);
            return recorded.get();
        }
        var supportCase = cases.save(SupportCase.forServiceDeskIncident(orderId, number, url, shortDescription,
                status, assignmentGroup, Instant.now(clock)));
        audit.record(orderId, AuditEvent.ActorType.SYSTEM, Actors.SERVICENOW, AuditActions.RAISE_CASE,
                AuditEvent.Outcome.SUCCEEDED, CaseWording.SERVICE_DESK_RAISED.formatted(number), shortDescription);
        return supportCase;
    }

    /**
     * Follows a service-desk incident whose Correlation ID no longer names the case's order: one that names none frees
     * the order, and one that names another order takes the case there.
     *
     * @return whether the incident names another order than the case's, or none, and was followed
     */
    boolean followOrder(SupportCase supportCase, String number, SupportCase.Status status, String assignmentGroup,
            boolean incidentFinal, UUID incidentOrderId, Instant now) {
        if (incidentOrderId == null) {
            // Followed even while resolved, so that the case learns when its incident becomes final.
            var wasOpen = supportCase.getStatus() != SupportCase.Status.RESOLVED;
            if (supportCase.followIncident(SupportCase.Status.RESOLVED, assignmentGroup, incidentFinal, false, now)
                    && wasOpen) {
                audit.record(supportCase.getOrderId(), AuditEvent.ActorType.SYSTEM, Actors.SERVICENOW,
                        AuditActions.FOLLOW_INCIDENT, AuditEvent.Outcome.SUCCEEDED,
                        CaseWording.NO_LONGER_NAMES_ORDER.formatted(number), null);
            }
            return true;
        }
        if (!incidentOrderId.equals(supportCase.getOrderId())) {
            follow(supportCase, number, incidentOrderId, status, assignmentGroup);
            return true;
        }
        return false;
    }

    /** Moves a service-desk case to the order its incident names now, or opens it again, with entries on the timeline. */
    private void follow(SupportCase supportCase, String number, UUID orderId, SupportCase.Status status,
            String assignmentGroup) {
        var before = supportCase.getOrderId();
        var wasResolved = supportCase.getStatus() == SupportCase.Status.RESOLVED;
        if (supportCase.followServiceDeskIncident(orderId, status, assignmentGroup, Instant.now(clock))) {
            if (!orderId.equals(before)) {
                audit.record(before, AuditEvent.ActorType.SYSTEM, Actors.SERVICENOW, AuditActions.FOLLOW_INCIDENT,
                        AuditEvent.Outcome.SUCCEEDED, CaseWording.NOW_ABOUT_ORDER.formatted(number, orderId), null);
            }
            audit.record(orderId, AuditEvent.ActorType.SYSTEM, Actors.SERVICENOW, AuditActions.FOLLOW_INCIDENT,
                    AuditEvent.Outcome.SUCCEEDED, CaseTexts.describeServiceDeskIncident(number, status, wasResolved),
                    null);
        }
    }
}
