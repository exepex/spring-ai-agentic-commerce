package io.github.exepex.commerce.mcpserver.security;

import io.github.exepex.commerce.agents.AgentDefinitions;
import io.github.exepex.commerce.agents.AgentDefinitionsAutoConfiguration;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import java.util.Map;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.autoconfigure.McpServerStreamableHttpWebMvcAutoConfiguration;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Agent authentication for an MCP server that declares its {@link ServerTools}: the token registry, the filter on the
 * protected paths, and the streamable-HTTP transport, configured like Spring AI's own but carrying the authenticated
 * agent's id into every tool call.
 */
@AutoConfiguration(before = McpServerStreamableHttpWebMvcAutoConfiguration.class,
        after = AgentDefinitionsAutoConfiguration.class)
@ConditionalOnBean({ServerTools.class, AgentDefinitions.class})
@EnableConfigurationProperties({McpServerProperties.class, McpServerStreamableHttpProperties.class})
public class McpServerSecurityAutoConfiguration {

    @Bean
    AgentRegistry agentRegistry(McpServerProperties properties, AgentDefinitions definitions, ServerTools serverTools) {
        return new AgentRegistry(properties, definitions, serverTools);
    }

    @Bean
    AgentAuthenticationFilter agentAuthenticationFilter(AgentRegistry agents, McpServerProperties properties) {
        return new AgentAuthenticationFilter(agents, properties.protectedPaths());
    }

    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(JsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .contextExtractor(request -> McpTransportContext.create(Map.of(CallingAgent.CONTEXT_KEY,
                        request.servletRequest().getAttribute(CallingAgent.REQUEST_ATTRIBUTE))))
                .build();
    }
}
