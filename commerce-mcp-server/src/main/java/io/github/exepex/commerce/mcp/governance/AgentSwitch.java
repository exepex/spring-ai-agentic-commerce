package io.github.exepex.commerce.mcp.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** The kill switch of one agent, and who last changed it. An agent without one is on. */
@Entity
@Table(name = "agent_switch")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentSwitch {

    @Id
    @Column(name = "agent_id")
    private String agentId;

    @Getter
    private boolean enabled;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "changed_at")
    private Instant changedAt;
}
