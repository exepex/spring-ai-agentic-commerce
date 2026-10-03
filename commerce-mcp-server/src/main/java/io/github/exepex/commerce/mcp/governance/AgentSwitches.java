package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.mcp.constants.AuditActions;
import io.github.exepex.commerce.mcp.constants.AuditSummaries;
import io.github.exepex.commerce.mcp.exception.AgentNotFoundException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
        var states = new LinkedHashMap<String, Boolean>();
        for (var agent : definitions.all()) {
            states.put(agent.id(), isEnabled(agent.id()));
        }
        return states;
    }

    @Transactional
    public Map<String, Boolean> set(String agentId, boolean enabled, String by) {
        if (definitions.all().stream().noneMatch(agent -> agent.id().equals(agentId))) {
            throw new AgentNotFoundException(agentId);
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
        audit.record(null, AuditEvent.ActorType.HUMAN, by,
                enabled ? AuditActions.SWITCH_ON_AGENT : AuditActions.SWITCH_OFF_AGENT, AuditEvent.Outcome.SUCCEEDED,
                (enabled ? AuditSummaries.SWITCHED_ON : AuditSummaries.SWITCHED_OFF).formatted(agentId), null);
        return all();
    }
}
