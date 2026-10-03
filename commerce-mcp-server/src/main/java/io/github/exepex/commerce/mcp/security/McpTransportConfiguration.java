package io.github.exepex.commerce.mcp.security;

import io.github.exepex.commerce.mcp.constants.SecurityValues;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * The streamable-HTTP MCP transport, configured like Spring AI's default but passing the authenticated agent's id
 * into every tool call's {@link McpTransportContext}.
 */
@Configuration(proxyBeanMethods = false)
class McpTransportConfiguration {

    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(JsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .contextExtractor(request -> McpTransportContext.create(Map.of(SecurityValues.AGENT_ID_CONTEXT_KEY,
                        request.servletRequest().getAttribute(SecurityValues.AGENT_ID_ATTRIBUTE))))
                .build();
    }
}
