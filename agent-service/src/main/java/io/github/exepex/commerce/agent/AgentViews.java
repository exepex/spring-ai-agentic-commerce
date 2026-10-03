package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinition;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the agents API answers with, and how the agent definitions and their switches turn into it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AgentViews {

    record AgentView(String id, Boolean enabled, String model, String effort, List<String> tools) {}

    record AgentsView(boolean modelConfigured, boolean slackConfigured, boolean servicenowConfigured,
            List<AgentView> agents) {}

    /** An agent's {@code enabled} is {@code null} when its switch is not among those read. */
    static AgentsView toView(boolean modelConfigured, AgentProperties properties, List<AgentDefinition> agents,
            Map<String, Boolean> switches) {
        return new AgentsView(modelConfigured, properties.slack().isConfigured(), properties.servicenow().isConfigured(),
                agents.stream().map(agent -> toView(agent, properties, switches)).toList());
    }

    /** The agent's tools on every MCP server that is configured; tools of other servers carry its name. */
    private static AgentView toView(AgentDefinition agent, AgentProperties properties, Map<String, Boolean> switches) {
        Stream<String> tools = agent.commerceTools().stream();
        if (properties.servicenow().isConfigured()) {
            tools = Stream.concat(tools, agent.servicenowTools().stream().map(tool -> "servicenow:" + tool));
        }
        if (properties.slack().isConfigured()) {
            tools = Stream.concat(tools, agent.slackTools().stream().map(tool -> "slack:" + tool));
        }
        return new AgentView(agent.id(), switches.get(agent.id()), agent.model(), agent.effort(), tools.toList());
    }
}
