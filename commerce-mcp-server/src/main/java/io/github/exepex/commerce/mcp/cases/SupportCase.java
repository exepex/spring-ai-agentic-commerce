package io.github.exepex.commerce.mcp.cases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A problem that needs handling, worked as one ServiceNow incident: first by the incident agent, then by a team when
 * a person is needed. The incident's state is read back into the case, so the shop knows who has it.
 */
@Entity
@Table(name = "support_case")
public class SupportCase {

    public enum Status {
        /** Recorded; its incident is not in ServiceNow yet. */
        PENDING,
        /** Its incident is in ServiceNow, with the incident agent. */
        WITH_AGENT,
        /** Its incident is with a team or a person. */
        WITH_TEAM,
        /** Its incident is resolved or closed. */
        RESOLVED
    }

    private static final int MAX_TITLE_LENGTH = 160;

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    private CaseType type;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String title;

    private String description;

    @Column(name = "raised_by")
    private String raisedBy;

    @Column(name = "incident_number")
    private String incidentNumber;

    @Column(name = "incident_url")
    private String incidentUrl;

    @Column(name = "assignment_group")
    private String assignmentGroup;

    /** Its incident goes straight to the default team, not to the agent: a person already had the work. */
    @Column(name = "for_people")
    private boolean forPeople;

    /** Its incident is closed or cancelled, so it can no longer be reopened and is not read back any more. */
    @Column(name = "incident_final")
    private boolean incidentFinal;

    /**
     * Its incident was reopened while the order already had a newer open case of the same problem: it is open, but that
     * newer case stays the one a problem raised again goes to.
     */
    @Column(name = "reopened_beside_open_case")
    private boolean reopenedBesideOpenCase;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SupportCase() {
        // for JPA
    }

    SupportCase(UUID orderId, CaseType type, String description, String raisedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.type = type;
        this.status = Status.PENDING;
        this.title = type.titleFor(orderId);
        this.description = description;
        this.raisedBy = raisedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A case for an incident the service desk raised: it starts in ServiceNow, with whoever has the incident. */
    static SupportCase forServiceDeskIncident(UUID orderId, String number, String url, String shortDescription,
            Status status, String group, Instant now) {
        SupportCase supportCase = new SupportCase(orderId, CaseType.SERVICE_DESK,
                "Raised by the service desk in ServiceNow as " + number + ".", CaseService.SERVICENOW, now);
        supportCase.title = shortDescription.length() <= MAX_TITLE_LENGTH ? shortDescription
                : shortDescription.substring(0, MAX_TITLE_LENGTH);
        supportCase.incidentNumber = number;
        supportCase.incidentUrl = url;
        supportCase.status = status;
        supportCase.assignmentGroup = group;
        return supportCase;
    }

    /**
     * Follows a service-desk incident seen open again: about the order its Correlation ID names now, and with whoever
     * has it, also when it was resolved before and has been reopened.
     *
     * @return whether anything changed
     */
    boolean followServiceDeskIncident(UUID incidentOrderId, Status incidentStatus, String group, Instant now) {
        boolean moved = !incidentOrderId.equals(orderId);
        boolean reopened = status == Status.RESOLVED;
        if (!moved && !reopened) {
            return false;
        }
        orderId = incidentOrderId;
        status = incidentStatus;
        assignmentGroup = group;
        updatedAt = now;
        return true;
    }

    /** Links the case to the incident created for it. Only the first incident counts. */
    boolean linkIncident(String number, String url, Instant now) {
        if (incidentNumber != null) {
            return false;
        }
        incidentNumber = number;
        incidentUrl = url;
        if (status == Status.PENDING) {
            status = Status.WITH_AGENT;
        }
        updatedAt = now;
        return true;
    }

    /**
     * Takes over who has the incident now, and whether it is final: closed or cancelled rather than only resolved.
     *
     * @param besideOpenCase whether the order has another open case of the same problem, should the incident have been
     *     reopened
     * @return whether who has the incident changed
     */
    boolean followIncident(Status incidentStatus, String group, boolean finalState, boolean besideOpenCase,
            Instant now) {
        incidentFinal = incidentStatus == Status.RESOLVED && finalState;
        if (incidentStatus == status && Objects.equals(group, assignmentGroup)) {
            return false;
        }
        if (status == Status.RESOLVED) {
            reopenedBesideOpenCase = besideOpenCase;
        }
        status = incidentStatus;
        assignmentGroup = group;
        updatedAt = now;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public CaseType getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getRaisedBy() {
        return raisedBy;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public String getIncidentUrl() {
        return incidentUrl;
    }

    public String getAssignmentGroup() {
        return assignmentGroup;
    }

    public boolean isForPeople() {
        return forPeople;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
