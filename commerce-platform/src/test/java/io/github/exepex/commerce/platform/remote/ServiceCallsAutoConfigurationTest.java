package io.github.exepex.commerce.platform.remote;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ServiceCallsAutoConfigurationTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ServiceCallsAutoConfiguration.class));

    @Test
    void servicesCallEachOtherThroughApacheHttpClientWithAPoolSizedForLoad() {
        contexts.run(context -> {
            assertThat(context.getBean(ClientHttpRequestFactoryBuilder.class))
                    .isInstanceOf(HttpComponentsClientHttpRequestFactoryBuilder.class);
            assertThat(context.getBean(ServiceCallProperties.class).maxConnectionsPerRoute()).isEqualTo(200);
        });
    }

    @Test
    void anAnswerIsNeverAReasonToSendTheRequestAgain() {
        var retries = new ServiceCallsAutoConfiguration.NoRetryOnStatus();

        assertThat(retries.retryRequest(new BasicHttpResponse(503), 1, new BasicHttpContext())).isFalse();
        assertThat(retries.retryRequest(new BasicHttpResponse(429), 1, new BasicHttpContext())).isFalse();
    }
}
