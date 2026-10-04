package io.github.exepex.commerce.platform.remote;

import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.context.annotation.Bean;

/**
 * How every service calls another over HTTP with Apache HttpClient:
 * <ul>
 * <li>a connection pool sized for many requests at once ({@code commerce.service-calls});</li>
 * <li>a request is never repeated because of the answer's status. HttpClient would otherwise send it again after a
 * 503, POSTs included, so one checkout could place or charge twice, or one refund be asked for twice. A request that
 * failed to connect is still retried, for idempotent methods only.</li>
 * </ul>
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration",
        "org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration"})
@ConditionalOnClass({ClientHttpRequestFactoryBuilder.class, HttpClientBuilder.class})
@EnableConfigurationProperties(ServiceCallProperties.class)
public class ServiceCallsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ClientHttpRequestFactoryBuilder<?> clientHttpRequestFactoryBuilder(ServiceCallProperties properties) {
        return ClientHttpRequestFactoryBuilder.httpComponents()
                .withConnectionManagerCustomizer(connections -> connections
                        .setMaxConnTotal(properties.maxConnections())
                        .setMaxConnPerRoute(properties.maxConnectionsPerRoute()))
                .withHttpClientCustomizer(client -> client.setRetryStrategy(new NoRetryOnStatus()));
    }

    /** Retries what failed to connect, as HttpClient does, but never because of the answer's status. */
    static final class NoRetryOnStatus extends DefaultHttpRequestRetryStrategy {

        @Override
        public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
            return false;
        }
    }
}
