package io.github.exepex.commerce.agent.dto;

import java.util.List;

/** The agents, and which of the model, Slack and ServiceNow are configured, as the agents API shows them. */
public record AgentsView(boolean modelConfigured, boolean slackConfigured, boolean servicenowConfigured,
        List<AgentView> agents) {}
