package io.github.exepex.commerce.mcp.governance;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Records what happens, tagged with the current trace so each entry links to its distributed trace. */
@Service
public class AuditTrail {

    private final AuditEventRepository events;
    private final ObjectProvider<Tracer> tracer;
    private final Clock clock;

    AuditTrail(AuditEventRepository events, ObjectProvider<Tracer> tracer, Clock clock) {
        this.events = events;
        this.tracer = tracer;
        this.clock = clock;
    }

    public void record(UUID orderId, AuditEvent.ActorType actorType, String actor, String action,
            AuditEvent.Outcome outcome, String summary, String details) {
        events.save(new AuditEvent(Instant.now(clock), orderId, actorType, actor, action, outcome, summary, details,
                currentTraceId()));
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
