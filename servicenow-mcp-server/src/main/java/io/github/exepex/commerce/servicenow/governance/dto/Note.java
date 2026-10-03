package io.github.exepex.commerce.servicenow.governance.dto;

import java.util.UUID;

/** A note added to a shop case, to be sent to its incident as a work note. */
public record Note(UUID id, String text) {}
