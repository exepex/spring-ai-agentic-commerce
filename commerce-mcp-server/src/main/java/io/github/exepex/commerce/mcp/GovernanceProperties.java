package io.github.exepex.commerce.mcp;

import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import io.github.exepex.commerce.mcp.dto.Agent;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The governance rules, set in {@code commerce.governance}. What each agent may do (its tools, whether it is
 * customer-scoped) comes from its definition in the agent-definitions module; only its secret is configured here.
 *
 * @param refundApprovalThreshold refunds above this amount wait for a human to approve them
 * @param agents each agent's credentials, by agent id
 */
@ConfigurationProperties(ConfigKeys.GOVERNANCE_PREFIX)
public record GovernanceProperties(BigDecimal refundApprovalThreshold, Map<String, Agent> agents) {}
