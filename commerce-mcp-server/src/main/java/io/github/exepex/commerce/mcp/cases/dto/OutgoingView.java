package io.github.exepex.commerce.mcp.cases.dto;

import java.util.List;

/**
 * A case with what is still to be sent to ServiceNow. Its {@code incidentNumber} is empty while the case has no
 * incident yet.
 */
public record OutgoingView(CaseView supportCase, List<NoteView> unsentNotes) {}
