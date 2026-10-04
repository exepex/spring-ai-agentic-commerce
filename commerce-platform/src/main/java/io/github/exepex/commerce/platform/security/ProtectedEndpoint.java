package io.github.exepex.commerce.platform.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** One endpoint only services may call: a method and a path pattern such as {@code /api/payments/{orderId}/refunds}. */
record ProtectedEndpoint(HttpMethod method, PathPattern path) {

    private static final String SEPARATOR = " ";

    static ProtectedEndpoint parse(String endpoint) {
        var separator = endpoint.trim().indexOf(SEPARATOR);
        if (separator < 0) {
            throw new InvalidProtectedEndpointException(endpoint);
        }
        return new ProtectedEndpoint(HttpMethod.valueOf(endpoint.substring(0, separator).trim()),
                PathPatternParser.defaultInstance.parse(endpoint.substring(separator + 1).trim()));
    }

    boolean matches(String method, String path) {
        return this.method.matches(method) && this.path.matches(PathContainer.parsePath(path));
    }
}
