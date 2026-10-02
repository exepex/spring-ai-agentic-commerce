package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The agents' kill switches, kept in the database so they survive a restart and every service reads the same state.
 * agent-service reads a switch before each run; the MCP server refuses every tool call of a switched-off agent except
 * handing work to a human.
 */
@Service
public class AgentSwitches {

    private final AgentSwitchRepository switches;
    private final AgentDefinitions definitions;
    private final AuditTrail audit;
    private final Clock clock;

    AgentSwitches(AgentSwitchRepository switches, AgentDefinitions definitions, AuditTrail audit, Clock clock) {
        this.switches = switches;
        this.definitions = definitions;
        this.audit = audit;
        this.clock = clock;
    }

    public boolean isEnabled(String agentId) {
        return switches.findById(agentId).map(AgentSwitch::isEnabled).orElse(true);
    }

    /** Every agent and whether it is on. */
    public Map<String, Boolean> all() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        for (AgentDefinition agent : definitions.all()) {
            states.put(agent.id(), isEnabled(agent.id()));
        }
        return states;
    }

    @Transactional
    public Map<String, Boolean> set(String agentId, boolean enabled, String by) {
        if (definitions.all().stream().noneMatch(agent -> agent.id().equals(agentId))) {
            throw new GovernanceException(HttpStatus.NOT_FOUND, "There is no agent " + agentId);
        }
        switches.save(new AgentSwitch(agentId, enabled, by, Instant.now(clock)));
        audit.record(null, AuditEvent.ActorType.HUMAN, by, enabled ? "switch_on_agent" : "switch_off_agent",
                AuditEvent.Outcome.SUCCEEDED, (enabled ? "Switched on " : "Switched off ") + agentId, null);
        return all();
    }
}
