package io.github.exepex.commerce.servicenow;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The ServiceNow instance and how incidents flow through it, under {@code commerce.servicenow}.
 *
 * @param instanceUrl the instance, such as {@code https://dev12345.service-now.com}; empty to run without ServiceNow
 * @param username the integration user the agent works as; incidents it claims are assigned to this user
 * @param password the integration user's password
 * @param agent the agent that works incidents; the poller claims incidents for it and records that as it
 * @param agentGroup the assignment group whose new, unassigned incidents the agent picks up
 * @param staleAfter how long a claimed incident may stay unfinished before it goes to the default team
 * @param closeCode the resolution code set when the agent resolves an incident
 * @param defaultTeam the team that gets an incident nobody else should: when the agent cannot finish it
 * @param teams the teams the agent can hand an incident to, by key
 */
@ConfigurationProperties("commerce.servicenow")
public record ServiceNowProperties(String instanceUrl, String username, String password, String agent, String agentGroup,
        Duration staleAfter, String closeCode, String defaultTeam, Map<String, Team> teams) {

    /**
     * @param group the ServiceNow assignment group's name
     * @param handles what the team handles, in words the agent uses to choose it
     */
    public record Team(String group, String handles) {}

    public ServiceNowProperties {
        if (teams == null || !teams.containsKey(defaultTeam)) {
            throw new IllegalStateException("commerce.servicenow.default-team must name one of the configured teams");
        }
        teams = Map.copyOf(teams);
    }

    public boolean isConfigured() {
        return instanceUrl != null && !instanceUrl.isBlank();
    }
}
