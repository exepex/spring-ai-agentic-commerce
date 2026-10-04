package io.github.exepex.commerce.platform.events;

import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Announces a service's events on Kafka through a transactional outbox, as soon as the service declares where they go
 * and has a database to keep them in.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration"})
@ConditionalOnClass({KafkaTemplate.class, JdbcClient.class})
@ConditionalOnBean({KafkaTemplate.class, EventRoute.class, DataSource.class, PlatformTransactionManager.class})
@EnableConfigurationProperties(OutboxProperties.class)
public class EventPublishingAutoConfiguration {

    @Bean
    OutboxStore outboxStore(DataSource dataSource, OutboxProperties properties) {
        return new OutboxStore(JdbcClient.create(dataSource), properties);
    }

    @Bean
    OutboxRelay outboxRelay(OutboxStore outbox, KafkaTemplate<String, Object> kafkaTemplate,
            PlatformTransactionManager transactionManager, ObjectProvider<TracePropagation> tracing,
            OutboxProperties properties) {
        return new OutboxRelay(outbox, kafkaTemplate, new TransactionTemplate(transactionManager),
                tracing.getIfAvailable(() -> TracePropagation.NONE), properties);
    }

    @Bean
    OutboxWriter outboxWriter(OutboxStore outbox, List<EventRoute<?>> routes,
            KafkaTemplate<String, Object> kafkaTemplate, ObjectProvider<TracePropagation> tracing,
            OutboxRelay relay) {
        return new OutboxWriter(outbox, routes, KafkaSerializers.valueSerializerOf(kafkaTemplate.getProducerFactory()),
                tracing.getIfAvailable(() -> TracePropagation.NONE), relay);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterBinder.class)
    static class Metrics {

        @Bean
        OutboxMetrics outboxMetrics(OutboxStore outbox, OutboxProperties properties) {
            return new OutboxMetrics(outbox, properties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Tracer.class)
    @ConditionalOnBean({Tracer.class, Propagator.class})
    static class Tracing {

        @Bean
        TracePropagation tracePropagation(Tracer tracer, Propagator propagator) {
            return new MicrometerTracePropagation(tracer, propagator);
        }
    }
}
