package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Stream;
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

    record AgentView(String id, boolean enabled, String model, String effort, List<String> tools) {}

    record AgentsView(boolean modelConfigured, boolean slackConfigured, List<AgentView> agents) {}

    record Switch(boolean enabled) {}

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

    @GetMapping("/api/agents")
    AgentsView agents() {
        boolean slackConfigured = properties.slack().isConfigured();
        return new AgentsView(modelConfigured, slackConfigured, List.of(
                view(definitions.get(AgentSwitchboard.SHOPPING_ASSISTANT), slackConfigured),
                view(definitions.get(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT), slackConfigured)));
    }

    private AgentView view(AgentDefinition agent, boolean slackConfigured) {
        List<String> tools = slackConfigured
                ? Stream.concat(agent.commerceTools().stream(), agent.slackTools().stream().map(tool -> "slack:" + tool))
                        .toList()
                : agent.commerceTools();
        return new AgentView(agent.id(), switchboard.isEnabled(agent.id()), agent.model(), agent.effort(), tools);
    }

    @PutMapping("/api/agents/{agentId}")
    AgentsView setEnabled(@PathVariable String agentId, @RequestBody Switch request) {
        switchboard.set(agentId, request.enabled());
        return agents();
    }
}
