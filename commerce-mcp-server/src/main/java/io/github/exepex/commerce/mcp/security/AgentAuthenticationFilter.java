package io.github.exepex.commerce.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets an agent in only with its own bearer token, on the MCP endpoint and the agent API. The agent's id is put on
 * the request, where the MCP transport passes it to every tool call: the model cannot claim to be another agent.
 */
@Component
@RequiredArgsConstructor
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    public static final String AGENT_ID_ATTRIBUTE = "commerce.agentId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AgentRegistry agents;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/mcp") || path.startsWith("/api/agent/"));
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
