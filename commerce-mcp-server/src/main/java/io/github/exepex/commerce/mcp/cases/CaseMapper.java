package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.governance.api.dto.CaseStatus;
import io.github.exepex.commerce.governance.api.dto.CaseView;
import io.github.exepex.commerce.governance.api.dto.NoteView;
import io.github.exepex.commerce.governance.api.dto.OutgoingCase;
import io.github.exepex.commerce.mcp.cases.dto.Outgoing;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * How cases and their notes are shown through the case API, as the governance contract words them, and how a case
 * status in that contract reads as the case's own.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CaseMapper {

    static CaseView toView(SupportCase supportCase) {
        return new CaseView(supportCase.getId(), supportCase.getOrderId(), supportCase.getType().name(),
                toContract(supportCase.getStatus()), supportCase.getTitle(), supportCase.getDescription(),
                supportCase.getRaisedBy(), supportCase.getIncidentNumber(), supportCase.getIncidentUrl(),
                supportCase.getAssignmentGroup(), supportCase.isForPeople(), supportCase.getCreatedAt(),
                supportCase.getUpdatedAt());
    }

    static NoteView toView(CaseNote note) {
        return new NoteView(note.getId(), note.getText(), note.getCreatedAt());
    }

    static OutgoingCase toView(Outgoing outgoing) {
        return new OutgoingCase(toView(outgoing.supportCase()),
                outgoing.unsentNotes().stream().map(CaseMapper::toView).toList());
    }

    /** The contract names each status as the case does. */
    static SupportCase.Status toStatus(CaseStatus status) {
        return SupportCase.Status.valueOf(status.name());
    }

    private static CaseStatus toContract(SupportCase.Status status) {
        return CaseStatus.valueOf(status.name());
    }
}
