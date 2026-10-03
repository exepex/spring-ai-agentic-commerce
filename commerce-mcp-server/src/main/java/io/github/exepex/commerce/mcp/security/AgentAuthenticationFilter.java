package io.github.exepex.commerce.mcp.security;

import io.github.exepex.commerce.mcp.constants.ApiPaths;
import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.mcp.constants.SecurityValues;
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

    private final AgentRegistry agents;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return !(path.startsWith(ApiPaths.MCP) || path.startsWith(ApiPaths.AGENT_API));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        var agentId = authorization != null && authorization.startsWith(SecurityValues.BEARER_PREFIX)
                ? agents.agentWithToken(authorization.substring(SecurityValues.BEARER_PREFIX.length()))
                : Optional.<String>empty();
        if (agentId.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), ErrorMessages.AGENT_TOKEN_REQUIRED);
            return;
        }
        request.setAttribute(SecurityValues.AGENT_ID_ATTRIBUTE, agentId.get());
        chain.doFilter(request, response);
    }
}
