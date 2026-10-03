package io.github.exepex.commerce.mcpserver.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.agents.exception.MissingAgentDefinitionException;
import io.github.exepex.commerce.mcpserver.exception.MissingAgentTokenException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRegistryTest {

    private static final AgentDefinitions DEFINITIONS = AgentDefinitions.load();

    private static McpServerProperties tokens(Map<String, AgentToken> agents) {
        return new McpServerProperties(agents, List.of("/mcp"));
    }

    private final AgentRegistry commerce = new AgentRegistry(tokens(Map.of(
            "shopping-assistant", new AgentToken("assistant-token"),
            "incident-agent", new AgentToken("incident-token"))), DEFINITIONS, AgentDefinition::commerceTools);

    @Test
    void aTokenNamesItsAgentAndNoOther() {
        assertThat(commerce.agentWithToken("incident-token")).contains("incident-agent");
        assertThat(commerce.agentWithToken("assistant-token")).contains("shopping-assistant");
        assertThat(commerce.agentWithToken("someone-elses-token")).isEmpty();
    }

    @Test
    void anAgentMayCallOnlyTheToolsItsDefinitionListsForThisServer() {
        assertThat(commerce.mayCall("shopping-assistant", "propose_order")).isTrue();
        assertThat(commerce.mayCall("shopping-assistant", "notify_customer")).isFalse();
        assertThat(commerce.mayCall("unknown-agent", "propose_order")).isFalse();
    }

    @Test
    void anAgentThatMayUseTheServerButHasNoTokenStopsTheServer() {
        var onlyTheAssistant = tokens(Map.of("shopping-assistant", new AgentToken("assistant-token")));

        assertThatThrownBy(() -> new AgentRegistry(onlyTheAssistant, DEFINITIONS, AgentDefinition::commerceTools))
                .isInstanceOf(MissingAgentTokenException.class)
                .hasMessage("No token is configured for agent incident-agent");
    }

    @Test
    void anAgentWithoutToolsOnTheServerNeedsNoToken() {
        var servicenow = new AgentRegistry(tokens(Map.of("incident-agent", new AgentToken("incident-token"))),
                DEFINITIONS, AgentDefinition::servicenowTools);

        assertThat(servicenow.definitionOf("shopping-assistant")).isEmpty();
        assertThat(servicenow.tokenOf("incident-agent")).isEqualTo("incident-token");
    }

    @Test
    void aTokenForAnAgentWithoutADefinitionStopsTheServer() {
        var stranger = tokens(Map.of(
                "shopping-assistant", new AgentToken("assistant-token"),
                "incident-agent", new AgentToken("incident-token"),
                "stranger", new AgentToken("stranger-token")));

        assertThatThrownBy(() -> new AgentRegistry(stranger, DEFINITIONS, AgentDefinition::commerceTools))
                .isInstanceOf(MissingAgentDefinitionException.class);
    }
}
