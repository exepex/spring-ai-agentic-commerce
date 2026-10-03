package io.github.exepex.commerce.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:outbox-schema.sql",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false",
        "commerce.events.outbox.poll-interval=200ms"})
class OutboxIntegrationTest {

    static final String TOPIC = "outbox.test-events";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    record PriceChanged(UUID productId, BigDecimal price, Instant changedAt) {}

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Shop {

        @Bean
        EventRoute<PriceChanged> priceChanges() {
            return EventRoute.of(PriceChanged.class, TOPIC, event -> event.productId().toString());
        }
    }

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcClient jdbc;

    @Test
    void aCommittedEventReachesKafkaAsTheProducerWouldHaveWrittenItAndLeavesTheOutbox() {
        var committed = new PriceChanged(UUID.randomUUID(), new BigDecimal("19.90"), Instant.parse("2026-10-03T12:00:00Z"));
        var rolledBack = new PriceChanged(UUID.randomUUID(), new BigDecimal("0.01"), Instant.parse("2026-10-03T12:00:01Z"));

        transactions.executeWithoutResult(status -> events.publishEvent(committed));
        transactions.executeWithoutResult(status -> {
            events.publishEvent(rolledBack);
            status.setRollbackOnly();
        });

        var received = receive(1);
        assertThat(received).singleElement().satisfies(event -> {
            assertThat(event.key()).isEqualTo(committed.productId().toString());
            assertThat(event.value()).isEqualTo(asTheProducerWritesIt(committed));
        });
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
                jdbc.sql("select count(*) from public.outbox_event").query(Long.class).single()).isZero());
    }

    private static String asTheProducerWritesIt(PriceChanged event) {
        try (var serializer = new JacksonJsonSerializer<PriceChanged>()) {
            serializer.configure(Map.of("spring.json.add.type.headers", false), false);
            return new String(serializer.serialize(TOPIC, event));
        }
    }

    private static ArrayList<ConsumerRecord<String, String>> receive(int expected) {
        var received = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(TOPIC));
            var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (received.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            }
            // Anything a rolled-back transaction raised would arrive with the committed event; give it the chance.
            consumer.poll(Duration.ofSeconds(2)).forEach(received::add);
        }
        return received;
    }
}
