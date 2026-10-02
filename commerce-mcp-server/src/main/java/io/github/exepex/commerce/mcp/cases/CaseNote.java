package io.github.exepex.commerce.mcp.cases;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Something added to a case after it was opened, sent to its incident as a work note. */
@Entity
@Table(name = "case_note")
public class CaseNote {

    @Id
    private UUID id;

    @Column(name = "case_id")
    private UUID caseId;

    private String text;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected CaseNote() {
        // for JPA
    }

    CaseNote(UUID caseId, String text, Instant now) {
        this.id = UUID.randomUUID();
        this.caseId = caseId;
        this.text = text;
        this.createdAt = now;
    }

    void markSent(Instant now) {
        if (sentAt == null) {
            sentAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaseId() {
        return caseId;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
