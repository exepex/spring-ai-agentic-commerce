package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.mcp.cases.dto.CaseView;
import io.github.exepex.commerce.mcp.cases.dto.NoteView;
import io.github.exepex.commerce.mcp.cases.dto.Outgoing;
import io.github.exepex.commerce.mcp.cases.dto.OutgoingView;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How cases and their notes are shown through the case API. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CaseMapper {

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

    static OutgoingView toView(Outgoing outgoing) {
        return new OutgoingView(toView(outgoing.supportCase()),
                outgoing.unsentNotes().stream().map(CaseMapper::toView).toList());
    }
}
