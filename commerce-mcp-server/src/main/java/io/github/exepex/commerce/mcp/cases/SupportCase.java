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

    /** Takes over who has the incident now. Returns whether anything changed. */
    boolean followIncident(Status incidentStatus, String group, Instant now) {
        if (incidentStatus == status && Objects.equals(group, assignmentGroup)) {
            return false;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
