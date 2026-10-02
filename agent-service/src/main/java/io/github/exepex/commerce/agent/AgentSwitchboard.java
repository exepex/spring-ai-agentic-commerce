package io.github.exepex.commerce.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * The kill switch for each agent. A switched-off agent never calls the model: the shopping assistant says it is
 * offline, and the order-exceptions agent hands every event to a human instead.
 */
@Component
public class AgentSwitchboard {

    public static final String SHOPPING_ASSISTANT = "shopping-assistant";
    public static final String ORDER_EXCEPTIONS_AGENT = "order-exceptions-agent";

    private final Map<String, Boolean> enabled = new ConcurrentHashMap<>(Map.of(
            SHOPPING_ASSISTANT, true,
            ORDER_EXCEPTIONS_AGENT, true));

    public boolean isEnabled(String agentId) {
        return enabled.getOrDefault(agentId, false);
    }

    public Map<String, Boolean> all() {
        return Map.copyOf(enabled);
    }

    public void set(String agentId, boolean on) {
        if (!enabled.containsKey(agentId)) {
            throw new IllegalArgumentException("Unknown agent " + agentId);
        }
        enabled.put(agentId, on);
    }
}
