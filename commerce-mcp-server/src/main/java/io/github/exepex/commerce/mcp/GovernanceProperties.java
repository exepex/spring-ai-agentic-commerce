package io.github.exepex.commerce.mcp;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The governance rules, set in {@code commerce.governance}.
 *
 * @param refundApprovalThreshold refunds above this amount wait for a human to approve them
 * @param agents each agent, by agent id
 */
@ConfigurationProperties("commerce.governance")
public record GovernanceProperties(BigDecimal refundApprovalThreshold, Map<String, Agent> agents) {

    /**
     * @param token the bearer token the agent authenticates with
     * @param tools the only tools the agent may call
     * @param customerScoped whether the agent acts for one customer, and may only touch that customer's orders
     */
    public record Agent(String token, List<String> tools, boolean customerScoped) {}
}
