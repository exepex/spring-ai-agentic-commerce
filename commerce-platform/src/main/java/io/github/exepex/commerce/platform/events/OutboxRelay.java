package io.github.exepex.commerce.platform.events;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.util.RawValue;

/**
 * Moves events from the outbox to Kafka, oldest first, and removes each once Kafka has it. An event Kafka does not
 * take stays, with every event after it, and is sent again next round: consumers may see an event twice, never lose
 * one. One instance of a service relays at a time, so the events of one order or product leave in the order they were
 * written. The relay runs as soon as a transaction writes an event, and polls in case another instance wrote it.
 */
@Slf4j
class OutboxRelay implements SmartLifecycle {

    /** Below the web server's graceful shutdown phase ({@code SmartLifecycle.DEFAULT_PHASE - 1024}). */
    static final int SHUTS_DOWN_AFTER_THE_WEB_SERVER = SmartLifecycle.DEFAULT_PHASE - 2048;

    private final OutboxStore outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final TransactionTemplate transactions;
    private final TracePropagation tracing;
    private final OutboxProperties properties;
    private final Semaphore wakeUps = new Semaphore(0);
    private volatile boolean running;
    private Thread worker;

    OutboxRelay(OutboxStore outbox, KafkaTemplate<String, Object> kafka, TransactionTemplate transactions,
            TracePropagation tracing, OutboxProperties properties) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.transactions = transactions;
        this.tracing = tracing;
        this.properties = properties;
    }

    void wakeUp() {
        wakeUps.release();
    }

    @Override
    public void start() {
        running = true;
        worker = Thread.ofPlatform().name("outbox-relay").daemon().start(this::relayUntilStopped);
    }

    @Override
    public void stop() {
        running = false;
        wakeUp();
        try {
            worker.join(properties.sendTimeout().plusSeconds(1).toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Stops after the web server has finished its requests, so the events they wrote still leave before shutdown. */
    @Override
    public int getPhase() {
        return SHUTS_DOWN_AFTER_THE_WEB_SERVER;
    }

    private void relayUntilStopped() {
        while (running) {
            try {
                if (relayBatch() < properties.batchSize()) {
                    awaitWork();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException failure) {
                log.warn("Could not relay the outbox {}; trying again shortly", properties.table(), failure);
                awaitWorkQuietly();
            }
        }
    }

    private void awaitWork() throws InterruptedException {
        if (wakeUps.tryAcquire(properties.pollInterval().toMillis(), TimeUnit.MILLISECONDS)) {
            wakeUps.drainPermits();
        }
    }

    private void awaitWorkQuietly() {
        try {
            awaitWork();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    /**
     * Sends the oldest waiting events and removes those Kafka took, stopping at the first one it did not.
     *
     * @return how many events left the outbox
     */
    int relayBatch() {
        var relayed = transactions.execute(status -> {
            if (!outbox.claimRelay()) {
                return 0;
            }
            var batch = outbox.nextBatch(properties.batchSize());
            var sends = new ArrayList<CompletableFuture<SendResult<String, Object>>>(batch.size());
            for (var row : batch) {
                tracing.continueTrace(TraceHeaders.decode(row.traceHeaders()), row.topic() + " relay",
                        () -> sends.add(kafka.send(row.topic(), row.key(), new RawValue(row.payload()))));
            }
            var sent = new ArrayList<Long>(batch.size());
            for (var index = 0; index < batch.size() && tookIt(sends.get(index), batch.get(index)); index++) {
                sent.add(batch.get(index).id());
            }
            outbox.remove(sent);
            return sent.size();
        });
        return relayed == null ? 0 : relayed;
    }

    private boolean tookIt(CompletableFuture<SendResult<String, Object>> send, OutboxRow row) {
        try {
            send.get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException | TimeoutException notTaken) {
            log.warn("Kafka did not take event {} for {}; sending it again shortly", row.id(), row.topic(), notTaken);
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
