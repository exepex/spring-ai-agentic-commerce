package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The settings in {@code application.yml} that the code reads by name. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfigKeys {

    public static final String COMMERCE_PREFIX = "commerce";
    public static final String INCIDENTS_TOPIC = "${commerce.topics.incidents}";
    /** The incident listener only starts when the ServiceNow MCP server is configured. */
    public static final String INCIDENT_LISTENER_AUTO_STARTUP = "#{'${commerce.servicenow.mcp-url:}' != ''}";
    public static final String ANTHROPIC_API_KEY = "spring.ai.anthropic.api-key";
}
