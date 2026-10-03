package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.ApiPaths;
import io.github.exepex.commerce.agent.constants.ConfigKeys;
import io.github.exepex.commerce.agent.dto.AgentsView;
import io.github.exepex.commerce.agent.dto.ChatRequest;
import io.github.exepex.commerce.agent.dto.Reply;
import io.github.exepex.commerce.agent.dto.Switch;
import io.github.exepex.commerce.agents.AgentDefinitions;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
class AgentController {

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
        this.modelConfigured = !environment.getProperty(ConfigKeys.ANTHROPIC_API_KEY, "").isBlank();
    }

    @PostMapping(ApiPaths.ASSISTANT_CHAT)
    Reply chat(@Valid @RequestBody ChatRequest request) {
        return assistant.chat(request.conversationId(), request.customerEmail(), request.message());
    }

    /** An agent's {@code enabled} is {@code null} when the MCP server, which keeps the switches, cannot be reached. */
    @GetMapping(ApiPaths.AGENTS)
    AgentsView agents() {
        return AgentMapper.toView(modelConfigured, properties, definitions.all(), switchesOrNone());
    }

    @PutMapping(ApiPaths.AGENT)
    AgentsView setEnabled(@PathVariable String agentId, @Valid @RequestBody Switch request) {
        switchboard.set(agentId, request.enabled(), request.by());
        return agents();
    }

    private Map<String, Boolean> switchesOrNone() {
        try {
            return switchboard.all();
        } catch (RuntimeException unreachable) {
            log.warn("Could not read the agents' kill switches", unreachable);
            return Map.of();
        }
    }
}
