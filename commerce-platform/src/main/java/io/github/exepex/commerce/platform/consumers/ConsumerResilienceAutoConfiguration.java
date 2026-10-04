package io.github.exepex.commerce.platform.consumers;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Every service's Kafka listeners retry a failed event a few times with growing pauses, then park it on its
 * dead-letter topic and go on with the next. A service that needs its own retry rule declares its own error handler
 * and can still park events with the {@link DeadLetterPublishingRecoverer} declared here.
 */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean(KafkaTemplate.class)
@EnableConfigurationProperties(ConsumerProperties.class)
public class ConsumerResilienceAutoConfiguration {

    static final String CONSUMER_THREAD_PREFIX = "kafka-consumer-";

    /**
     * Kafka consumers poll on platform threads even when the service handles requests on virtual threads: the Kafka
     * client polls inside {@code synchronized} code, which on Java 21 holds a virtual thread's carrier for the whole
     * poll. A dozen long-polling consumers would hold every carrier, and the service would stop answering requests.
     */
    @Bean
    static BeanPostProcessor kafkaConsumersOnPlatformThreads() {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
                    factory.getContainerProperties().setListenerTaskExecutor(
                            new SimpleAsyncTaskExecutor(CONSUMER_THREAD_PREFIX));
                }
                return bean;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    DeadLetterPublishingRecoverer deadLetterRecoverer(ProducerFactory<Object, Object> producers,
            KafkaTemplate<Object, Object> kafkaTemplate) {
        return DeadLetters.recoverer(producers, kafkaTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer deadLetters, ConsumerProperties properties) {
        var backOff = new ExponentialBackOffWithMaxRetries(properties.retries());
        backOff.setInitialInterval(properties.firstRetry().toMillis());
        backOff.setMultiplier(2);
        backOff.setMaxInterval(properties.longestBackOff().toMillis());
        return new DefaultErrorHandler(deadLetters, backOff);
    }
}
