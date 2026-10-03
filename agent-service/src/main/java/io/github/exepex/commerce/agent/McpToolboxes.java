package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * Connects each agent to its MCP servers with its own credentials, and hands it only the tools on its allowlist.
 * The commerce and ServiceNow MCP servers check the same permissions again on every call.
 *
 * <p>Connections open on first use and are reopened after a failure, so the agents start even when an MCP server is
 * not up yet, and carry on after it restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class McpToolboxes {

    private static final String SLACK = "slack";
    private static final String SERVICENOW = "servicenow";
    /** The commerce MCP server enforces the kill switch on every call itself. */
    private static final BooleanSupplier ENFORCED_BY_THE_SERVER = () -> true;

    private final AgentProperties properties;
    private final AgentDefinitions definitions;
    private final AgentSwitchboard switchboard;
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    /** The shopping assistant's tools; {@code customerEmail} is always the signed-in customer's. */
    List<ToolCallback> shoppingAssistantTools() {
        AgentDefinition agent = definitions.get(AgentSwitchboard.SHOPPING_ASSISTANT);
        return toolsFrom(AgentSwitchboard.SHOPPING_ASSISTANT,
                () -> commerceClient(AgentSwitchboard.SHOPPING_ASSISTANT), agent.commerceTools(), agent.customerScoped(),
                ENFORCED_BY_THE_SERVER);
    }

    /** The incident agent's tools: the shop's, ServiceNow's and, when configured, Slack's. */
    List<ToolCallback> incidentAgentTools() {
        AgentDefinition agent = definitions.get(AgentSwitchboard.INCIDENT_AGENT);
        List<ToolCallback> tools = new ArrayList<>(toolsFrom(AgentSwitchboard.INCIDENT_AGENT,
                () -> commerceClient(AgentSwitchboard.INCIDENT_AGENT), agent.commerceTools(), agent.customerScoped(),
                ENFORCED_BY_THE_SERVER));
        tools.addAll(toolsFrom(SERVICENOW, () -> servicenowClient(AgentSwitchboard.INCIDENT_AGENT), agent.servicenowTools(),
                false, ENFORCED_BY_THE_SERVER));
        if (properties.slack().isConfigured() && !agent.slackTools().isEmpty()) {
            try {
                tools.addAll(toolsFrom(SLACK, this::slackClient, agent.slackTools(), false,
                        () -> isSwitchedOn(AgentSwitchboard.INCIDENT_AGENT)));
            } catch (RuntimeException slackDown) {
                log.warn("Slack MCP server unavailable; the agent runs without Slack", slackDown);
                clients.remove(SLACK);
            }
        }
        return tools;
    }

    /** Calls a ServiceNow tool directly as the incident agent, without a model: to hand an incident to a team. */
    McpSchema.CallToolResult callAsIncidentAgent(String tool, Map<String, Object> arguments) {
        return onLiveConnection(SERVICENOW, () -> servicenowClient(AgentSwitchboard.INCIDENT_AGENT),
                client -> client.callTool(new McpSchema.CallToolRequest(tool, arguments)));
    }

    private List<ToolCallback> toolsFrom(String connection, Supplier<McpSyncClient> client, List<String> allowedTools,
            boolean injectsCustomer, BooleanSupplier agentSwitchedOn) {
        ToolCallback[] mcpTools = onLiveConnection(connection, client,
                live -> McpClients.allowedTools(live, allowedTools));
        return Arrays.stream(mcpTools)
                .<ToolCallback>map(mcpTool -> new AgentToolCallback(mcpTool, injectsCustomer, agentSwitchedOn))
                .toList();
    }

    /** Fails closed: if the switch cannot be read, the third-party tool is not called. */
    private boolean isSwitchedOn(String agentId) {
        try {
            return switchboard.isEnabled(agentId);
        } catch (RuntimeException unreadable) {
            log.warn("Could not read the kill switch of {}; refusing its third-party tool call", agentId, unreadable);
            return false;
        }
    }

    /**
     * After an MCP server restarts, the old connection and session are gone and the first request fails. Then the
     * connection is dropped and the request made once more on a new one. Callers only pass requests that are safe to
     * repeat.
     */
    private <T> T onLiveConnection(String connection, Supplier<McpSyncClient> client, Function<McpSyncClient, T> request) {
        try {
            return request.apply(client.get());
        } catch (RuntimeException connectionLost) {
            log.info("MCP request on {} failed ({}); reconnecting once", connection, connectionLost.getMessage());
            McpSyncClient lost = clients.remove(connection);
            if (lost != null) {
                lost.close();
            }
            return request.apply(client.get());
        }
    }

    private McpSyncClient commerceClient(String agentId) {
        return connect(agentId, properties.agents().mcpUrl(), properties.agents().tokenOf(agentId));
    }

    private McpSyncClient servicenowClient(String agentId) {
        return connect(SERVICENOW, properties.servicenow().mcpUrl(), properties.agents().tokenOf(agentId));
    }

    private McpSyncClient slackClient() {
        return connect(SLACK, properties.slack().mcpUrl(), properties.slack().apiKey());
    }

    private McpSyncClient connect(String name, String url, String bearerToken) {
        return clients.compute(name, (key, existing) -> existing != null && existing.isInitialized()
                ? existing
                : McpClients.open(name, url, bearerToken));
    }

    @PreDestroy
    void disconnect() {
        clients.values().forEach(McpSyncClient::closeGracefully);
    }
}
