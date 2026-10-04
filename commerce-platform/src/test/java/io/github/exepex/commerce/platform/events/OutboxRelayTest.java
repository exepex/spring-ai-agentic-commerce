package io.github.exepex.commerce.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.util.RawValue;

class OutboxRelayTest {

    private final OutboxStore outbox = mock(OutboxStore.class);

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);

    private final OutboxRelay relay = new OutboxRelay(outbox, kafka,
            new TransactionTemplate(mock(PlatformTransactionManager.class)), TracePropagation.NONE,
            new OutboxProperties("orders.outbox_event", 10, Duration.ofSeconds(1), Duration.ofMillis(200)));

    private final OutboxRow placed = new OutboxRow(1, "order.events", "order-1", "{\"type\":\"ORDER_CONFIRMED\"}", null);
    private final OutboxRow cancelled = new OutboxRow(2, "order.events", "order-1", "{\"type\":\"ORDER_CANCELLED\"}",
            null);
    private final OutboxRow shipped = new OutboxRow(3, "order.events", "order-2", "{\"type\":\"ORDER_SHIPPED\"}", null);

    @Test
    void eventsLeaveOldestFirstAndAreRemovedOnceKafkaHasThem() {
        when(outbox.claimRelay()).thenReturn(true);
        when(outbox.nextBatch(10)).thenReturn(List.of(placed, cancelled));
        when(kafka.send(anyString(), anyString(), any())).thenReturn(taken(), taken());

        assertThat(relay.relayBatch()).isEqualTo(2);

        var sends = inOrder(kafka);
        sends.verify(kafka).send("order.events", "order-1", new RawValue(placed.payload()));
        sends.verify(kafka).send("order.events", "order-1", new RawValue(cancelled.payload()));
        verify(outbox).remove(List.of(1L, 2L));
    }

    @Test
    void anEventKafkaDidNotTakeStaysWithEveryEventAfterIt() {
        when(outbox.claimRelay()).thenReturn(true);
        when(outbox.nextBatch(10)).thenReturn(List.of(placed, cancelled, shipped));
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(taken(), CompletableFuture.failedFuture(new IllegalStateException("broker down")), taken());

        assertThat(relay.relayBatch()).isEqualTo(1);

        verify(outbox).remove(List.of(1L));
    }

    @Test
    void anEventKafkaDoesNotAnswerForInTimeIsSentAgainLater() {
        when(outbox.claimRelay()).thenReturn(true);
        when(outbox.nextBatch(10)).thenReturn(List.of(placed));
        when(kafka.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

        assertThat(relay.relayBatch()).isZero();

        verify(outbox).remove(List.of());
    }

    @Test
    void whileAnotherInstanceRelaysNothingIsSent() {
        when(outbox.claimRelay()).thenReturn(false);

        assertThat(relay.relayBatch()).isZero();

        verify(outbox, never()).nextBatch(10);
        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    void theRelayStopsOnlyAfterTheWebServerHasFinishedItsRequests() {
        assertThat(relay.getPhase()).isLessThan(SmartLifecycle.DEFAULT_PHASE - 1024);
    }

    private static CompletableFuture<SendResult<String, Object>> taken() {
        return CompletableFuture.completedFuture(null);
    }
}
