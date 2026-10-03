package io.github.exepex.commerce.mcp.cases.dto;

import java.time.Instant;
import java.util.UUID;

/** A note added to a case, as the case sync shows it. */
public record NoteView(UUID id, String text, Instant createdAt) {}
