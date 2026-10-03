package io.github.exepex.commerce.mcp.governance;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Records what happens, tagged with the current trace so each entry links to its distributed trace. */
@Service
@RequiredArgsConstructor
public class AuditTrail {

    private final AuditEventRepository events;
    private final ObjectProvider<Tracer> tracer;
    private final Clock clock;

    public void record(UUID orderId, AuditEvent.ActorType actorType, String actor, String action,
            AuditEvent.Outcome outcome, String summary, String details) {
        recordAt(Instant.now(clock), orderId, actorType, actor, action, outcome, summary, details);
    }

    /** Records something that happened earlier, at the time it happened. */
    public void recordAt(Instant occurredAt, UUID orderId, AuditEvent.ActorType actorType, String actor, String action,
            AuditEvent.Outcome outcome, String summary, String details) {
        events.save(new AuditEvent(occurredAt, orderId, actorType, actor, action, outcome, summary, details,
                currentTraceId(), null));
    }

    /**
     * Records an event another service announced, at the time it happened, once per order: Kafka can deliver the
     * same event again, and a redelivered one is skipped.
     */
    void recordSystemEvent(UUID sourceEventId, Instant occurredAt, UUID orderId, String service, String action,
            String summary, String details) {
        if (events.existsBySourceEventIdAndOrderId(sourceEventId, orderId)) {
            return;
        }
        try {
            events.save(new AuditEvent(occurredAt, orderId, AuditEvent.ActorType.SYSTEM, service, action,
                    AuditEvent.Outcome.SUCCEEDED, summary, details, currentTraceId(), sourceEventId));
        } catch (DataIntegrityViolationException recordedMeanwhile) {
            // The same event was recorded by a delivery that ran at the same moment.
        }
    }

    public List<AuditEvent> timelineOf(UUID orderId) {
        return events.findByOrderIdOrderByOccurredAt(orderId);
    }

    public List<AuditEvent> recent() {
        return events.findTop200ByOrderByOccurredAtDesc();
    }

    private String currentTraceId() {
        Tracer currentTracer = tracer.getIfAvailable();
        Span span = currentTracer == null ? null : currentTracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
