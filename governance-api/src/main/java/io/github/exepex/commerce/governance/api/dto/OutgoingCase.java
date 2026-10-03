package io.github.exepex.commerce.governance.api.dto;

import java.util.List;

/** A case with what is still to be sent to ServiceNow: its incident, when it has none, and its unsent notes. */
public record OutgoingCase(CaseView supportCase, List<NoteView> unsentNotes) {}
