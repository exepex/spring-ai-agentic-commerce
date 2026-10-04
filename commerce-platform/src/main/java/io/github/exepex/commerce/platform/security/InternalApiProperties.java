package io.github.exepex.commerce.platform.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The service token that guards the endpoints only other services may call: placing, charging, reserving, refunding.
 * A browser or anyone else who can reach a service cannot call them, so the refund approval limit and checkout cannot
 * be bypassed by calling a service directly.
 *
 * @param token              the token services present to each other
 * @param protectedEndpoints this service's endpoints that require it, as {@code METHOD /path/{variable}}
 * @param clients            the HTTP service groups this service calls with it
 */
@ConfigurationProperties("commerce.internal-api")
public record InternalApiProperties(String token, List<String> protectedEndpoints, List<String> clients) {

    public InternalApiProperties {
        protectedEndpoints = protectedEndpoints == null ? List.of() : List.copyOf(protectedEndpoints);
        clients = clients == null ? List.of() : List.copyOf(clients);
        if ((!protectedEndpoints.isEmpty() || !clients.isEmpty()) && (token == null || token.isBlank())) {
            throw new MissingInternalApiTokenException();
        }
    }
}
