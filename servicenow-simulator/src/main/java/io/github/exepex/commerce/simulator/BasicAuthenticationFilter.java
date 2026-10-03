package io.github.exepex.commerce.simulator;

import io.github.exepex.commerce.simulator.constants.ApiPaths;
import io.github.exepex.commerce.simulator.constants.ErrorMessages;
import io.github.exepex.commerce.simulator.constants.ServiceNowValues;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Lets the Table API in only with the integration user's basic-auth login, as a real instance does. */
@Component
class BasicAuthenticationFilter extends OncePerRequestFilter {

    private final byte[] expected;

    BasicAuthenticationFilter(SimulatorProperties properties) {
        this.expected = (ServiceNowValues.BASIC_AUTH_PREFIX + Base64.getEncoder().encodeToString(
                ServiceNowValues.CREDENTIALS.formatted(properties.username(), properties.password())
                        .getBytes(StandardCharsets.UTF_8)))
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ApiPaths.TABLE_API);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var presented = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (presented == null || !MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(ErrorMessages.NOT_AUTHENTICATED);
            return;
        }
        chain.doFilter(request, response);
    }
}
