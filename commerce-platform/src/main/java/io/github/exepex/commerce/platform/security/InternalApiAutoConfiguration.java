package io.github.exepex.commerce.platform.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroupConfigurer.ClientCallback;

/**
 * Guards a service's internal endpoints with the service token, and presents the token on its calls to the services
 * it names under {@code commerce.internal-api.clients}. Other clients, such as the agents' calls to the governance API
 * with their own tokens, are left alone. A service that lists neither is not affected.
 */
@AutoConfiguration
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    InternalApiFilter internalApiFilter(InternalApiProperties properties) {
        return new InternalApiFilter(properties.token(), properties.protectedEndpoints());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
    static class Calls {

        @Bean
        RestClientHttpServiceGroupConfigurer internalApiClients(InternalApiProperties properties) {
            if (properties.clients().isEmpty()) {
                return groups -> { };
            }
            var authorization = BearerTokens.authorization(properties.token());
            ClientCallback<RestClient.Builder> presentToken =
                    (group, client) -> client.defaultHeader(HttpHeaders.AUTHORIZATION, authorization);
            return groups -> groups.filterByName(properties.clients().toArray(String[]::new))
                    .forEachClient(presentToken);
        }
    }
}
