package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.constants.IncidentTexts;
import io.github.exepex.commerce.governance.api.dto.NoteView;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How a case's notes are written on its incident, so the incident shows which notes it already holds. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CaseNotes {

    /** The longest work note sent, as for the agent's own notes: a case's note can be this long before its marker. */
    private static final int MAX_WORK_NOTE_LENGTH = 4000;

    /** The note as a work note: its text, shortened if need be so that its marker always fits within the limit. */
    static String workNoteOf(NoteView note) {
        var ending = IncidentTexts.CASE_NOTE_ENDING.formatted(markerOf(note));
        var text = note.text();
        var room = MAX_WORK_NOTE_LENGTH - ending.length();
        return (text.length() <= room ? text : text.substring(0, room)) + ending;
    }

    /** The line that ends a case note's work note, so the incident shows which notes it already holds. */
    static String markerOf(NoteView note) {
        return IncidentTexts.CASE_NOTE_MARKER.formatted(note.id());
    }
}
