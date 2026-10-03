package io.github.exepex.commerce.mcp.tools.dto;

import java.util.UUID;

/** That a tool did what it was asked, with the id of what it created. */
public record Acknowledgement(UUID id, String message) {}
