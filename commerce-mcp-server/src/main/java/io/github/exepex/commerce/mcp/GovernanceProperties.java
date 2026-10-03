package io.github.exepex.commerce.mcp;

import io.github.exepex.commerce.mcp.constants.ConfigKeys;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The governance rules, set in {@code commerce.governance}. What each agent may do (its tools, whether it is
 * customer-scoped) comes from its definition in the agent-definitions module; its token is set under
 * {@code commerce.mcp.agents}.
 *
 * @param refundApprovalThreshold refunds above this amount wait for a human to approve them
 */
@ConfigurationProperties(ConfigKeys.GOVERNANCE_PREFIX)
public record GovernanceProperties(BigDecimal refundApprovalThreshold) {}
