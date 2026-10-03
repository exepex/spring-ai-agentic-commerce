package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.ConfigKeys;
import io.github.exepex.commerce.agent.dto.Agents;
import io.github.exepex.commerce.agent.dto.ServiceNow;
import io.github.exepex.commerce.agent.dto.Slack;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the agents connect and with which secrets, under {@code commerce}. What each agent is (model, effort, tools,
 * budget, prompt) comes from its definition in the agent-definitions module.
 *
 * @param agents the commerce MCP server and each agent's credentials
 * @param slack the Slack MCP server and the one channel agents may post in
 * @param servicenow the ServiceNow MCP server
 */
@ConfigurationProperties(ConfigKeys.COMMERCE_PREFIX)
public record AgentProperties(Agents agents, Slack slack, ServiceNow servicenow) {}
