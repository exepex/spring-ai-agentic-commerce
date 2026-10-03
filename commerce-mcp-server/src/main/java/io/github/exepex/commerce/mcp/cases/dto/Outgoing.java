package io.github.exepex.commerce.mcp.cases.dto;

import io.github.exepex.commerce.mcp.cases.CaseNote;
import io.github.exepex.commerce.mcp.cases.SupportCase;
import java.util.List;

/** A case with what is still to be sent to ServiceNow: its incident, when it has none, and its unsent notes. */
public record Outgoing(SupportCase supportCase, List<CaseNote> unsentNotes) {}
