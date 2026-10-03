package io.github.exepex.commerce.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClockAutoConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ClockAutoConfiguration.class));

    @Test
    void servicesTellTheTimeInUtc() {
        context.run(started -> assertThat(started.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC));
    }

    @Test
    void aTestCanFixTheClock() {
        var fixed = Clock.fixed(Instant.parse("2026-10-02T10:30:00Z"), ZoneOffset.UTC);
        context.withBean(Clock.class, () -> fixed)
                .run(started -> assertThat(started.getBean(Clock.class)).isSameAs(fixed));
    }
}
