package io.github.exepex.commerce.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxPropertiesTest {

    private static final Duration POLL = Duration.ofSeconds(1);
    private static final Duration SEND = Duration.ofSeconds(10);

    @Test
    void aSchemaQualifiedTableIsAccepted() {
        var properties = new OutboxProperties("orders.outbox_event", 500, POLL, SEND);

        assertThat(properties.table()).isEqualTo("orders.outbox_event");
    }

    @Test
    void anythingButAPlainTableNameIsRefusedAtStartup() {
        assertThatThrownBy(() -> new OutboxProperties("orders.outbox_event; drop table orders.customer_order", 500,
                POLL, SEND))
                .isInstanceOf(InvalidOutboxTableException.class);
        assertThatThrownBy(() -> new OutboxProperties("outbox_event", 500, POLL, SEND))
                .isInstanceOf(InvalidOutboxTableException.class);
    }

    @Test
    void anEmptyBatchIsRefusedAtStartup() {
        assertThatThrownBy(() -> new OutboxProperties("orders.outbox_event", 0, POLL, SEND))
                .isInstanceOf(InvalidOutboxTableException.class);
    }
}
