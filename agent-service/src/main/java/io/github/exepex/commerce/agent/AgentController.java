package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AgentController {

    record ChatRequest(@NotBlank String conversationId, @NotBlank @Email String customerEmail, @NotBlank String message) {}

    record AgentView(String id, Boolean enabled, String model, String effort, List<String> tools) {}

    record AgentsView(boolean modelConfigured, boolean slackConfigured, boolean servicenowConfigured,
            List<AgentView> agents) {}

    /** {@code by} is the person switching, for the audit trail. */
    record Switch(boolean enabled, @NotBlank @Size(max = 100) String by) {}

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentController.class);

    private final ShoppingAssistant assistant;
    private final AgentSwitchboard switchboard;
    private final AgentProperties properties;
    private final AgentDefinitions definitions;
    private final boolean modelConfigured;

    AgentController(ShoppingAssistant assistant, AgentSwitchboard switchboard, AgentProperties properties,
            AgentDefinitions definitions, Environment environment) {
        this.assistant = assistant;
        this.switchboard = switchboard;
        this.properties = properties;
        this.definitions = definitions;
        this.modelConfigured = !environment.getProperty("spring.ai.anthropic.api-key", "").isBlank();
    }

    @PostMapping("/api/assistant/chat")
    ShoppingAssistant.Reply chat(@Valid @RequestBody ChatRequest request) {
        return assistant.chat(request.conversationId(), request.customerEmail(), request.message());
    }

    /** An agent's {@code enabled} is {@code null} when the MCP server, which keeps the switches, cannot be reached. */
    @GetMapping("/api/agents")
    AgentsView agents() {
        Map<String, Boolean> switches;
        try {
            switches = switchboard.all();
        } catch (RuntimeException unreachable) {
            LOGGER.warn("Could not read the agents' kill switches", unreachable);
            switches = Map.of();
        }
        Map<String, Boolean> knownSwitches = switches;
        return new AgentsView(modelConfigured, properties.slack().isConfigured(), properties.servicenow().isConfigured(),
                definitions.all().stream().map(agent -> view(agent, knownSwitches)).toList());
    }

    /** The agent's tools on every MCP server that is configured; tools of other servers carry its name. */
    private AgentView view(AgentDefinition agent, Map<String, Boolean> switches) {
        Stream<String> tools = agent.commerceTools().stream();
        if (properties.servicenow().isConfigured()) {
            tools = Stream.concat(tools, agent.servicenowTools().stream().map(tool -> "servicenow:" + tool));
        }
        if (properties.slack().isConfigured()) {
            tools = Stream.concat(tools, agent.slackTools().stream().map(tool -> "slack:" + tool));
        }
        return new AgentView(agent.id(), switches.get(agent.id()), agent.model(), agent.effort(), tools.toList());
    }

    @PutMapping("/api/agents/{agentId}")
    AgentsView setEnabled(@PathVariable String agentId, @Valid @RequestBody Switch request) {
        switchboard.set(agentId, request.enabled(), request.by());
        return agents();
    }
}
