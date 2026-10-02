package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * Connects each agent to its MCP servers with its own credentials, and hands it only the tools on its allowlist.
 * The commerce MCP server checks the same permissions again on every call.
 *
 * <p>Connections open on first use and are reopened after a failure, so the agents start even when an MCP server is
 * not up yet, and carry on after it restarts.
 */
@Component
class McpToolboxes {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpToolboxes.class);
    private static final String SLACK = "slack";

    private final AgentProperties properties;
    private final AgentDefinitions definitions;
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    McpToolboxes(AgentProperties properties, AgentDefinitions definitions) {
        this.properties = properties;
        this.definitions = definitions;
    }

    /** The shopping assistant's tools; {@code customerEmail} is always the signed-in customer's. */
    List<ToolCallback> shoppingAssistantTools() {
        AgentDefinition agent = definitions.get(AgentSwitchboard.SHOPPING_ASSISTANT);
        return toolsFrom(AgentSwitchboard.SHOPPING_ASSISTANT,
                () -> commerceClient(AgentSwitchboard.SHOPPING_ASSISTANT), agent.commerceTools(), agent.customerScoped());
    }

    List<ToolCallback> orderExceptionsAgentTools() {
        AgentDefinition agent = definitions.get(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT);
        List<ToolCallback> tools = new ArrayList<>(
                toolsFrom(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT,
                        () -> commerceClient(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT), agent.commerceTools(),
                        agent.customerScoped()));
        if (properties.slack().isConfigured() && !agent.slackTools().isEmpty()) {
            try {
                tools.addAll(toolsFrom(SLACK, this::slackClient, agent.slackTools(), false));
            } catch (RuntimeException slackDown) {
                // Slack is a nice-to-have: without it the agent still does its job and records it in the audit trail.
                LOGGER.warn("Slack MCP server unavailable; the agent runs without Slack", slackDown);
                clients.remove(SLACK);
            }
        }
        return tools;
    }

    /** Calls a commerce tool directly, without a model: used when an agent is switched off or fails. */
    McpSchema.CallToolResult callAsOrderExceptionsAgent(String tool, Map<String, Object> arguments) {
        // Only used to hand work to a human: if the first attempt is lost, a second escalation beats none.
        return onLiveConnection(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT,
                () -> commerceClient(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT),
                client -> client.callTool(new McpSchema.CallToolRequest(tool, arguments)));
    }

    private List<ToolCallback> toolsFrom(String connection, Supplier<McpSyncClient> client, List<String> allowedTools,
            boolean injectsCustomer) {
        ToolCallback[] mcpTools = onLiveConnection(connection, client, live -> listTools(live, allowedTools));
        List<ToolCallback> tools = new ArrayList<>();
        for (ToolCallback mcpTool : mcpTools) {
            tools.add(new AgentToolCallback(mcpTool, injectsCustomer));
        }
        return tools;
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
            LOGGER.info("MCP request on {} failed ({}); reconnecting once", connection, connectionLost.getMessage());
            McpSyncClient lost = clients.remove(connection);
            if (lost != null) {
                lost.close();
            }
            return request.apply(client.get());
        }
    }

    private static ToolCallback[] listTools(McpSyncClient client, List<String> allowedTools) {
        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(client)
                .toolFilter((connection, tool) -> allowedTools.contains(tool.name()))
                .toolNamePrefixGenerator(McpToolNamePrefixGenerator.noPrefix())
                .build()
                .getToolCallbacks();
    }

    private McpSyncClient commerceClient(String agentId) {
        return connect(agentId, properties.agents().mcpUrl(), properties.agents().tokenOf(agentId));
    }

    private McpSyncClient slackClient() {
        return connect(SLACK, properties.slack().mcpUrl(), properties.slack().apiKey());
    }

    private McpSyncClient connect(String name, String url, String bearerToken) {
        return clients.compute(name, (key, existing) -> {
            if (existing != null && existing.isInitialized()) {
                return existing;
            }
            McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport.builder(url)
                            .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + bearerToken))
                            .build())
                    .clientInfo(new McpSchema.Implementation("agent-service/" + name, "1.0.0"))
                    .requestTimeout(Duration.ofSeconds(60))
                    .build();
            client.initialize();
            return client;
        });
    }

    @PreDestroy
    void disconnect() {
        clients.values().forEach(McpSyncClient::closeGracefully);
    }
}
