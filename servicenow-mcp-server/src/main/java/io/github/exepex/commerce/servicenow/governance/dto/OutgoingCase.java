package io.github.exepex.commerce.servicenow.governance.dto;

import java.util.List;

/** A case with what is still to be sent: its incident, when it has none, and the notes added to it since. */
public record OutgoingCase(Case supportCase, List<Note> unsentNotes) {}
