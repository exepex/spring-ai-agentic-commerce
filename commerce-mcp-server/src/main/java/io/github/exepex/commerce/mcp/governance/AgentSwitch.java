package io.github.exepex.commerce.mcp.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** The kill switch of one agent, and who last changed it. An agent without one is on. */
@Entity
@Table(name = "agent_switch")
public class AgentSwitch {

    @Id
    @Column(name = "agent_id")
    private String agentId;

    private boolean enabled;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "changed_at")
    private Instant changedAt;

    protected AgentSwitch() {
        // for JPA
    }

    AgentSwitch(String agentId, boolean enabled, String changedBy, Instant changedAt) {
        this.agentId = agentId;
        this.enabled = enabled;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
