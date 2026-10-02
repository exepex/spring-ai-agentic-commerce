package io.github.exepex.commerce.order;

import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Calls to other services are never repeated because of the answer's status. Apache HttpClient would otherwise send
 * a request again after a 503, POSTs included, so one checkout could place or charge twice. A request that failed to
 * connect is still retried, for idempotent methods only.
 */
@Configuration(proxyBeanMethods = false)
class HttpClientConfiguration {

    @Bean
    ClientHttpRequestFactoryBuilder<?> clientHttpRequestFactoryBuilder() {
        return ClientHttpRequestFactoryBuilder.httpComponents()
                .withHttpClientCustomizer(client -> client.setRetryStrategy(new DefaultHttpRequestRetryStrategy() {
                    @Override
                    public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
                        return false;
                    }
                }));
    }
}
