package io.github.exepex.commerce.platform.events;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The outbox's backlog, as the metrics report it, read straight from the table with no relay running. */
@Testcontainers
class OutboxBacklogTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static SingleConnectionDataSource dataSource;
    static JdbcClient jdbc;

    @BeforeAll
    static void createTheOutbox() {
        dataSource = new SingleConnectionDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword(), true);
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                create table outbox_event (
                    id bigint generated always as identity primary key, topic varchar(255) not null,
                    event_key varchar(255), payload text not null, trace_headers text,
                    created_at timestamptz not null default now())""").update();
    }

    @AfterAll
    static void close() {
        dataSource.destroy();
    }

    @Test
    void theMetricsShowHowManyEventsWaitAndHowLongTheOldestHasWaited() {
        var properties = new OutboxProperties("public.outbox_event", 500, Duration.ofSeconds(1), Duration.ofSeconds(10));
        var outbox = new OutboxStore(jdbc, properties);
        var registry = new SimpleMeterRegistry();
        new OutboxMetrics(outbox, properties).bindTo(registry);

        assertThat(registry.get("commerce.outbox.waiting").gauge().value()).isZero();
        assertThat(registry.get("commerce.outbox.oldest.age").gauge().value()).isZero();

        outbox.add("order.events", "order-1", "{}", null);
        jdbc.sql("""
                insert into outbox_event (topic, payload, created_at)
                values ('order.events', '{}', now() - interval '90 seconds')""").update();

        assertThat(registry.get("commerce.outbox.waiting").gauge().value()).isEqualTo(2);
        assertThat(registry.get("commerce.outbox.oldest.age").gauge().value()).isBetween(89.0, 120.0);
    }
}
