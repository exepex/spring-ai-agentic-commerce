package io.github.exepex.commerce.servicenow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets an agent in only with its own bearer token, on the MCP endpoint. The agent's id is put on the request, where
 * the MCP transport passes it to every tool call: the model cannot claim to be another agent.
 */
@Component
class AgentAuthenticationFilter extends OncePerRequestFilter {

    static final String AGENT_ID_ATTRIBUTE = "servicenow.agentId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AgentRegistry agents;

    AgentAuthenticationFilter(AgentRegistry agents) {
        this.agents = agents;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        Optional<String> agentId = authorization != null && authorization.startsWith(BEARER_PREFIX)
                ? agents.agentWithToken(authorization.substring(BEARER_PREFIX.length()))
                : Optional.empty();
        if (agentId.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "An agent bearer token is required");
            return;
        }
        request.setAttribute(AGENT_ID_ATTRIBUTE, agentId.get());
        chain.doFilter(request, response);
    }
}
