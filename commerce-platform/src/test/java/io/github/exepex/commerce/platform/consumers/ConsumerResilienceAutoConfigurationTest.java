package io.github.exepex.commerce.platform.consumers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.CommonLoggingErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ExceptionClassifier;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.ExceptionMatcher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.core.exc.StreamReadException;

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
    void consumersPollOnPlatformThreadsEvenWhenRequestsRunOnVirtualOnes() {
        contexts.withPropertyValues("spring.threads.virtual.enabled=true").run(context -> {
            var executor = (SimpleAsyncTaskExecutor) context.getBean(ConcurrentKafkaListenerContainerFactory.class)
                    .getContainerProperties().getListenerTaskExecutor();
            var consumerThread = new CompletableFuture<Thread>();
            executor.execute(() -> consumerThread.complete(Thread.currentThread()));
            var thread = consumerThread.get(5, TimeUnit.SECONDS);

            assertThat(thread.isVirtual()).isFalse();
            assertThat(thread.getName()).startsWith(ConsumerResilienceAutoConfiguration.CONSUMER_THREAD_PREFIX);
        });
    }

    @Test
    void anEventIsParkedOnlyOnceKafkaHasItOnTheDeadLetterTopic() {
        contexts.run(context -> assertThat(ReflectionTestUtils.getField(
                context.getBean(DeadLetterPublishingRecoverer.class), "failIfSendResultIsError")).isEqualTo(true));
    }

    @Test
    void anEventThatIsNotValidJsonIsParkedAtOnceAndAnyOtherFailureIsRetried() {
        contexts.run(context -> {
            var handler = context.getBean(DefaultErrorHandler.class);
            var unreadable = new ListenerExecutionFailedException("listener failed",
                    new StreamReadException(null, "Unexpected character ('t')"));
            var databaseDown = new ListenerExecutionFailedException("listener failed",
                    new IllegalStateException("database down"));

            assertThat(retries(handler).match(unreadable)).isFalse();
            assertThat(retries(handler).match(databaseDown)).isTrue();
        });
    }

    /** Spring Kafka keeps the classification to itself; the test reads it to check which failures are retried. */
    private static ExceptionMatcher retries(DefaultErrorHandler handler) throws ReflectiveOperationException {
        var getter = ExceptionClassifier.class.getDeclaredMethod("getExceptionMatcher");
        getter.setAccessible(true);
        return (ExceptionMatcher) getter.invoke(handler);
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
