package io.github.exepex.commerce.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentDefinitionsTest {

    private final AgentDefinitions definitions = AgentDefinitions.load();

    @Test
    void loadsEveryShippedAgent() {
        assertThat(definitions.all()).extracting(AgentDefinition::id)
                .containsExactlyInAnyOrder("shopping-assistant", "incident-agent");

        AgentDefinition assistant = definitions.get("shopping-assistant");
        assertThat(assistant.customerScoped()).isTrue();
        assertThat(assistant.toolCallBudget()).isEqualTo(12);
        assertThat(assistant.commerceTools()).contains("propose_order").doesNotContain("notify_customer");
        assertThat(assistant.slackTools()).isEmpty();
        assertThat(assistant.servicenowTools()).isEmpty();
    }

    @Test
    void theIncidentAgentWorksServiceNowIncidentsWithShopTools() {
        AgentDefinition agent = definitions.get("incident-agent");

        assertThat(agent.customerScoped()).isFalse();
        assertThat(agent.servicenowTools()).contains("get_incident", "assign_to_team", "resolve_incident");
        assertThat(agent.commerceTools()).contains("get_order", "issue_refund").doesNotContain("propose_order");
    }

    @Test
    void addsTheSlackStepOnlyWhenAChannelIsConfigured() {
        AgentDefinition agent = definitions.get("incident-agent");

        assertThat(agent.systemPrompt("C123")).contains("in channel C123").doesNotContain("{slack");
        assertThat(agent.systemPrompt(null)).doesNotContain("conversations_add_message").doesNotContain("{slack");
    }

    @Test
    void refusesADefinitionWithAMissingSetting() {
        assertThatThrownBy(() -> AgentDefinitions.parse("broken.md", """
                ---
                id: broken
                model: claude-opus-5-5
                ---
                Do things.
                """)).hasMessageContaining("broken.md is missing 'effort'");
    }

    @Test
    void refusesAFileWithoutAHeader() {
        assertThatThrownBy(() -> AgentDefinitions.parse("plain.md", "Just instructions."))
                .hasMessageContaining("must start with a YAML header");
    }
}
