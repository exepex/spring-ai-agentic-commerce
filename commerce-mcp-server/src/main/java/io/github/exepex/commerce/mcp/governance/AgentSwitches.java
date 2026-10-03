package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The agents' kill switches, kept in the database so they survive a restart and every service reads the same state.
 * agent-service reads a switch before each run; the MCP server refuses every tool call of a switched-off agent except
 * handing work to a human.
 */
@Service
@RequiredArgsConstructor
public class AgentSwitches {

    private final AgentSwitchRepository switches;
    private final AgentDefinitions definitions;
    private final AuditTrail audit;
    private final JdbcClient jdbc;
    private final Clock clock;

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
        // One statement, so two first changes at once both succeed instead of both inserting the row; the last wins.
        jdbc.sql("""
                        insert into governance.agent_switch (agent_id, enabled, changed_by, changed_at)
                        values (:agentId, :enabled, :by, :now)
                        on conflict (agent_id) do update
                        set enabled = excluded.enabled, changed_by = excluded.changed_by, changed_at = excluded.changed_at""")
                .param("agentId", agentId)
                .param("enabled", enabled)
                .param("by", by)
                .param("now", Timestamp.from(Instant.now(clock)))
                .update();
        audit.record(null, AuditEvent.ActorType.HUMAN, by, enabled ? "switch_on_agent" : "switch_off_agent",
                AuditEvent.Outcome.SUCCEEDED, (enabled ? "Switched on " : "Switched off ") + agentId, null);
        return all();
    }
}
