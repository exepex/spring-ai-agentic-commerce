package io.github.exepex.commerce.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Lets a call to an internal endpoint through only with the service token; every other endpoint is left alone. */
public class InternalApiFilter extends OncePerRequestFilter {

    static final String TOKEN_REQUIRED = "This endpoint is for other services only";

    private final byte[] token;
    private final List<ProtectedEndpoint> endpoints;

    InternalApiFilter(String token, List<String> endpoints) {
        this.token = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        this.endpoints = endpoints.stream().map(ProtectedEndpoint::parse).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        return endpoints.stream().noneMatch(endpoint -> endpoint.matches(request.getMethod(), path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var presented = BearerTokens.tokenIn(request.getHeader(HttpHeaders.AUTHORIZATION))
                .map(value -> value.getBytes(StandardCharsets.UTF_8))
                .filter(value -> token.length > 0 && MessageDigest.isEqual(value, token));
        if (presented.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), TOKEN_REQUIRED);
            return;
        }
        chain.doFilter(request, response);
    }
}
