package io.github.exepex.commerce.mcpserver.security;

import io.github.exepex.commerce.mcpserver.constants.McpServerMessages;
import io.github.exepex.commerce.platform.security.BearerTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets an agent in only with its own bearer token, on the server's protected paths. The agent's id is put on the
 * request, where the MCP transport passes it to every tool call: the model cannot claim to be another agent.
 */
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    private final AgentRegistry agents;
    private final List<String> protectedPaths;

    public AgentAuthenticationFilter(AgentRegistry agents, List<String> protectedPaths) {
        this.agents = agents;
        this.protectedPaths = List.copyOf(protectedPaths);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return protectedPaths.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var agentId = BearerTokens.tokenIn(request.getHeader(HttpHeaders.AUTHORIZATION))
                .flatMap(agents::agentWithToken);
        if (agentId.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), McpServerMessages.AGENT_TOKEN_REQUIRED);
            return;
        }
        request.setAttribute(CallingAgent.REQUEST_ATTRIBUTE, agentId.get());
        chain.doFilter(request, response);
    }
}
