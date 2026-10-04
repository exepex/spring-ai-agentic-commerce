package io.github.exepex.commerce.platform.remote;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How many connections a service keeps open to the services it calls. Apache HttpClient's own default is five per
 * service, which made every checkout beyond the fifth at once wait for a connection to the catalog or payments.
 *
 * @param maxConnections         connections to all services together
 * @param maxConnectionsPerRoute connections to one service
 */
@ConfigurationProperties("commerce.service-calls")
public record ServiceCallProperties(
        @DefaultValue("400") int maxConnections,
        @DefaultValue("200") int maxConnectionsPerRoute) {}
