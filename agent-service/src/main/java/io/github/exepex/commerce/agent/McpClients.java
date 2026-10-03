package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.McpValues;
import io.github.exepex.commerce.platform.security.BearerTokens;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.HttpHeaders;

/** How a connection to an MCP server is opened, and how the tools an agent may use are read from it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class McpClients {

    /** A connection that presents the bearer token on every request, so the server knows who is calling. */
    static McpSyncClient open(String name, String url, String bearerToken) {
        var client = McpClient.sync(HttpClientStreamableHttpTransport.builder(url)
                        .requestBuilder(HttpRequest.newBuilder()
                                .header(HttpHeaders.AUTHORIZATION, BearerTokens.authorization(bearerToken)))
                        .build())
                .clientInfo(new McpSchema.Implementation(McpValues.CLIENT_NAME_PREFIX + name, McpValues.CLIENT_VERSION))
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        client.initialize();
        return client;
    }

    /** The server's tools that are on the allowlist, under their own names. */
    static ToolCallback[] allowedTools(McpSyncClient client, List<String> allowedTools) {
        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(client)
                .toolFilter((connection, tool) -> allowedTools.contains(tool.name()))
                .toolNamePrefixGenerator(McpToolNamePrefixGenerator.noPrefix())
                .build()
                .getToolCallbacks();
    }
}
