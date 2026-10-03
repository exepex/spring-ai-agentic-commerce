package io.github.exepex.commerce.platform.consumers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.CommonLoggingErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

class ConsumerResilienceAutoConfigurationTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class,
                    ConsumerResilienceAutoConfiguration.class));

    @Test
    void listenersRetryAndThenParkWhatTheyCannotHandle() {
        contexts.run(context -> {
            assertThat(context).hasSingleBean(DeadLetterPublishingRecoverer.class);
            assertThat(context).hasSingleBean(DefaultErrorHandler.class);
        });
    }

    @Test
    void aServiceWithItsOwnRetryRuleKeepsItAndCanStillParkEvents() {
        contexts.withUserConfiguration(OwnRetryRule.class).run(context -> {
            assertThat(context).hasSingleBean(CommonErrorHandler.class);
            assertThat(context.getBean(CommonErrorHandler.class)).isInstanceOf(CommonLoggingErrorHandler.class);
            assertThat(context).hasSingleBean(DeadLetterPublishingRecoverer.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnRetryRule {

        @Bean
        CommonErrorHandler ownRetryRule() {
            return new CommonLoggingErrorHandler();
        }
    }
}
