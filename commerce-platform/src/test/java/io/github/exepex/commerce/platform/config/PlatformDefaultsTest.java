package io.github.exepex.commerce.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PlatformDefaultsTest {

    @Test
    void everyServiceSendsAllItsTracesAndNoMetricsToJaeger() {
        var environment = new StandardEnvironment();

        new PlatformDefaults().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isEqualTo("http://localhost:4318/v1/traces");
    }

    @Test
    void aServiceCanStillChangeADefault() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application",
                Map.of("management.tracing.sampling.probability", "0.1")));

        new PlatformDefaults().postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("0.1");
    }
}
