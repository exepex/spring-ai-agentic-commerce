package io.github.exepex.commerce.servicenow.dto;

/**
 * A team the agent can hand an incident to.
 *
 * @param group the ServiceNow assignment group's name
 * @param handles what the team handles, in words the agent uses to choose it
 */
public record Team(String group, String handles) {}
