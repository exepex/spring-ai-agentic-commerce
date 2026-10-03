package io.github.exepex.commerce.servicenow.incidents.dto;

/** A team an incident can be handed to, by its key, and what it handles. */
public record TeamView(String team, String handles) {}
