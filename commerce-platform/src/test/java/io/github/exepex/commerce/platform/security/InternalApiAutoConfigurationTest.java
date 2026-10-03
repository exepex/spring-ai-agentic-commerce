package io.github.exepex.commerce.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

class InternalApiAutoConfigurationTest {

    private final WebApplicationContextRunner contexts = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InternalApiAutoConfiguration.class));

    @Test
    void aServiceWithInternalEndpointsGuardsThem() {
        contexts.withPropertyValues("commerce.internal-api.token=service-token",
                        "commerce.internal-api.protected-endpoints=POST /api/payments/{orderId}/refunds")
                .run(context -> assertThat(context).hasSingleBean(InternalApiFilter.class));
    }

    @Test
    void aServiceThatCallsInternalEndpointsPresentsTheToken() {
        contexts.withPropertyValues("commerce.internal-api.token=service-token",
                        "commerce.internal-api.clients[0]=catalog", "commerce.internal-api.clients[1]=payment")
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClientHttpServiceGroupConfigurer.class);
                    assertThat(context.getBean(InternalApiProperties.class).clients())
                            .containsExactly("catalog", "payment");
                });
    }

    @Test
    void aServiceWithoutInternalEndpointsStartsWithoutAToken() {
        contexts.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void endpointsListedInYamlAreGuarded() {
        contexts.withPropertyValues("commerce.internal-api.token=service-token",
                        "commerce.internal-api.protected-endpoints[0]=POST /api/payments",
                        "commerce.internal-api.protected-endpoints[1]=POST /api/payments/{orderId}/refunds")
                .run(context -> assertThat(context.getBean(InternalApiProperties.class).protectedEndpoints())
                        .containsExactly("POST /api/payments", "POST /api/payments/{orderId}/refunds"));
    }

    @Test
    void guardingWithoutATokenStopsTheServiceFromStarting() {
        contexts.withPropertyValues("commerce.internal-api.token=",
                        "commerce.internal-api.protected-endpoints[0]=POST /api/payments")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(MissingInternalApiTokenException.class));
    }

    @Test
    void aMalformedEndpointStopsTheServiceFromStarting() {
        contexts.withPropertyValues("commerce.internal-api.token=service-token",
                        "commerce.internal-api.protected-endpoints[0]=/api/payments")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().isInstanceOf(InvalidProtectedEndpointException.class));
    }
}
