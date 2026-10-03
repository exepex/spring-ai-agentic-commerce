package io.github.exepex.commerce.governance.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Something raised again about a case, to reach its incident as a work note. */
public record NoteView(UUID id, String text, Instant createdAt) {}
