package io.github.exepex.commerce.servicenow.governance.dto;

/** The incident opened for a case: its number, and the link where a person opens it. */
public record IncidentLink(String number, String url) {}
