package io.github.exepex.commerce.platform.events;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/** Announces a service's events on Kafka after commit, as soon as the service declares where they go. */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean({KafkaTemplate.class, EventRoute.class})
public class EventPublishingAutoConfiguration {

    @Bean
    AfterCommitEventPublisher afterCommitEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
            List<EventRoute<?>> routes) {
        return new AfterCommitEventPublisher(kafkaTemplate, routes);
    }
}
