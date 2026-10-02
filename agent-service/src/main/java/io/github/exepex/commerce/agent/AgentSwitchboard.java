package io.github.exepex.commerce.agent;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The kill switch for each agent. A switched-off agent never calls the model: the shopping assistant says it is
 * offline, and the order-exceptions agent hands every event to a human instead. The switches are kept by the
 * commerce MCP server, which also refuses a switched-off agent's tool calls, so they survive a restart and a switch
 * turned off mid-run stops the run.
 */
@Component
public class AgentSwitchboard {

    public static final String SHOPPING_ASSISTANT = "shopping-assistant";
    public static final String ORDER_EXCEPTIONS_AGENT = "order-exceptions-agent";

    private final AgentSwitchesApi switches;

    AgentSwitchboard(AgentSwitchesApi switches) {
        this.switches = switches;
    }

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
