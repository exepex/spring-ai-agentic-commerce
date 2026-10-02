package io.github.exepex.commerce.agent;

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
 * not up yet.
 */
@Component
class McpToolboxes {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpToolboxes.class);
    private static final String SLACK = "slack";

    private final AgentProperties properties;
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    McpToolboxes(AgentProperties properties) {
        this.properties = properties;
    }

    /** The shopping assistant's tools; {@code customerEmail} is always the signed-in customer's. */
    List<ToolCallback> shoppingAssistantTools() {
        AgentProperties.Agent agent = properties.agents().shoppingAssistant();
        return toolsFrom(commerceClient(AgentSwitchboard.SHOPPING_ASSISTANT, agent.token()), agent.tools(), true);
    }

    List<ToolCallback> orderExceptionsAgentTools() {
        AgentProperties.Agent agent = properties.agents().orderExceptionsAgent();
        List<ToolCallback> tools = new ArrayList<>(
                toolsFrom(commerceClient(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT, agent.token()), agent.tools(), false));
        if (properties.slack().isConfigured()) {
            try {
                tools.addAll(toolsFrom(slackClient(), properties.slack().tools(), false));
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
        AgentProperties.Agent agent = properties.agents().orderExceptionsAgent();
        return commerceClient(AgentSwitchboard.ORDER_EXCEPTIONS_AGENT, agent.token())
                .callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    private List<ToolCallback> toolsFrom(McpSyncClient client, List<String> allowedTools, boolean injectsCustomer) {
        ToolCallback[] mcpTools = SyncMcpToolCallbackProvider.builder()
                .mcpClients(client)
                .toolFilter((connection, tool) -> allowedTools.contains(tool.name()))
                .toolNamePrefixGenerator(McpToolNamePrefixGenerator.noPrefix())
                .build()
                .getToolCallbacks();
        List<ToolCallback> tools = new ArrayList<>();
        for (ToolCallback mcpTool : mcpTools) {
            tools.add(new AgentToolCallback(mcpTool, injectsCustomer));
        }
        return tools;
    }

    private McpSyncClient commerceClient(String agentId, String token) {
        return connect(agentId, properties.agents().mcpUrl(), token);
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
