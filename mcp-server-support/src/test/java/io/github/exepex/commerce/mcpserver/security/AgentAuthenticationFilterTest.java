package io.github.exepex.commerce.mcpserver.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exepex.commerce.agents.AgentDefinition;
import io.github.exepex.commerce.agents.AgentDefinitions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AgentAuthenticationFilterTest {

    private final AgentAuthenticationFilter filter = new AgentAuthenticationFilter(new AgentRegistry(
            new McpServerProperties(Map.of("shopping-assistant", new AgentToken("assistant-token"),
                    "incident-agent", new AgentToken("incident-token")), List.of("/mcp", "/api/agent/")),
            AgentDefinitions.load(), AgentDefinition::commerceTools), List.of("/mcp", "/api/agent/"));

    @Test
    void anAgentWithItsOwnTokenIsLetInUnderItsOwnId() throws Exception {
        var request = request("/mcp", "Bearer incident-token");
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(CallingAgent.REQUEST_ATTRIBUTE)).isEqualTo("incident-agent");
    }

    @Test
    void aProtectedPathWithoutAKnownTokenIsRefused() throws Exception {
        for (var authorization : new String[] {null, "Bearer someone-elses-token", "Basic dXNlcjpwYXNz"}) {
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();

            filter.doFilter(request("/api/agent/tool-calls", authorization), response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getErrorMessage()).isEqualTo("An agent bearer token is required");
            assertThat(chain.getRequest()).isNull();
        }
    }

    @Test
    void anOpenPathNeedsNoToken() throws Exception {
        var chain = new MockFilterChain();

        filter.doFilter(request("/api/cases", null), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private static MockHttpServletRequest request(String path, String authorization) {
        var request = new MockHttpServletRequest("POST", path);
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request;
    }
}
