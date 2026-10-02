package io.github.exepex.commerce.agent;

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
    private final boolean modelConfigured;

    AgentController(ShoppingAssistant assistant, AgentSwitchboard switchboard, AgentProperties properties,
            Environment environment) {
        this.assistant = assistant;
        this.switchboard = switchboard;
        this.properties = properties;
        this.modelConfigured = !environment.getProperty("spring.ai.anthropic.api-key", "").isBlank();
    }

    @PostMapping("/api/assistant/chat")
    ShoppingAssistant.Reply chat(@Valid @RequestBody ChatRequest request) {
        return assistant.chat(request.conversationId(), request.customerEmail(), request.message());
    }

    @GetMapping("/api/agents")
    AgentsView agents() {
        AgentProperties.Agents agents = properties.agents();
        List<String> exceptionsTools = properties.slack().isConfigured()
                ? Stream.concat(agents.orderExceptionsAgent().tools().stream(),
                        properties.slack().tools().stream().map(tool -> "slack:" + tool)).toList()
                : agents.orderExceptionsAgent().tools();
        return new AgentsView(modelConfigured, properties.slack().isConfigured(), List.of(
                new AgentView(AgentSwitchboard.SHOPPING_ASSISTANT, switchboard.isEnabled(AgentSwitchboard.SHOPPING_ASSISTANT),
                        agents.model(), agents.shoppingAssistant().effort(), agents.shoppingAssistant().tools()),
                new AgentView(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT,
                        switchboard.isEnabled(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT), agents.model(),
                        agents.orderExceptionsAgent().effort(), exceptionsTools)));
    }

    @PutMapping("/api/agents/{agentId}")
    AgentsView setEnabled(@PathVariable String agentId, @RequestBody Switch request) {
        switchboard.set(agentId, request.enabled());
        return agents();
    }
}
