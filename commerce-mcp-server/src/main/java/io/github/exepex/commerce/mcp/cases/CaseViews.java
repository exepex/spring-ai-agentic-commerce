package io.github.exepex.commerce.mcp.cases;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the case API answers with, and how cases and their notes turn into it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CaseViews {

    /** {@code forPeople} means its incident goes straight to the default team, not to the agent. */
    record CaseView(UUID id, UUID orderId, CaseType type, SupportCase.Status status, String title, String description,
            String raisedBy, String incidentNumber, String incidentUrl, String assignmentGroup, boolean forPeople,
            Instant createdAt, Instant updatedAt) {}

    record NoteView(UUID id, String text, Instant createdAt) {}

    /** {@code incidentNumber} is empty while the case has no incident yet. */
    record OutgoingView(CaseView supportCase, List<NoteView> unsentNotes) {}

    static CaseView toView(SupportCase supportCase) {
        return new CaseView(supportCase.getId(), supportCase.getOrderId(), supportCase.getType(),
                supportCase.getStatus(), supportCase.getTitle(), supportCase.getDescription(),
                supportCase.getRaisedBy(), supportCase.getIncidentNumber(), supportCase.getIncidentUrl(),
                supportCase.getAssignmentGroup(), supportCase.isForPeople(), supportCase.getCreatedAt(),
                supportCase.getUpdatedAt());
    }

    static NoteView toView(CaseNote note) {
        return new NoteView(note.getId(), note.getText(), note.getCreatedAt());
    }

    static OutgoingView toView(CaseService.Outgoing outgoing) {
        return new OutgoingView(toView(outgoing.supportCase()),
                outgoing.unsentNotes().stream().map(CaseViews::toView).toList());
    }
}
