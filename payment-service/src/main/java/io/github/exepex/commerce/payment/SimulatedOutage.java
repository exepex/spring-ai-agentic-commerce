package io.github.exepex.commerce.payment;

import io.github.exepex.commerce.payment.constants.ApiPaths;
import io.github.exepex.commerce.payment.constants.ErrorMessages;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A demo switch that makes the payment API answer 503, to show how the agents behave when a dependency is down.
 * Only {@code /api/payments} is affected; the switch itself stays reachable.
 */
@Component
class SimulatedOutage extends OncePerRequestFilter {

    private final AtomicBoolean active = new AtomicBoolean();

    boolean isActive() {
        return active.get();
    }

    void setActive(boolean outage) {
        active.set(outage);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ApiPaths.PAYMENTS);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!active.get()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(ErrorMessages.SIMULATED_OUTAGE);
    }
}
