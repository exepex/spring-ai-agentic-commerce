package io.github.exepex.commerce.agent;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The kill switch for each agent. A switched-off agent never calls the model: the shopping assistant says it is
 * offline, and the incident agent hands every incident to a team instead. The switches are kept by the
 * commerce MCP server, which also refuses a switched-off agent's tool calls, so they survive a restart and a switch
 * turned off mid-run stops the run.
 */
@Component
@RequiredArgsConstructor
public class AgentSwitchboard {

    public static final String SHOPPING_ASSISTANT = "shopping-assistant";
    public static final String INCIDENT_AGENT = "incident-agent";

    private final AgentSwitchesApi switches;

    /** Read before every run. Throws if the MCP server cannot be reached; callers decide what that means. */
    public boolean isEnabled(String agentId) {
        return Boolean.TRUE.equals(switches.all().get(agentId));
    }

    public Map<String, Boolean> all() {
        return switches.all();
    }

    public void set(String agentId, boolean on, String by) {
        switches.set(agentId, new AgentSwitchesApi.Change(on, by));
    }
}
