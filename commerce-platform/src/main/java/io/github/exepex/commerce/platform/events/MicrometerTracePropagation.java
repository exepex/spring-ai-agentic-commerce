package io.github.exepex.commerce.platform.events;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/** Propagates traces with the service's own tracer, in whatever format it sends traces in (W3C by default). */
@RequiredArgsConstructor
class MicrometerTracePropagation implements TracePropagation {

    private final Tracer tracer;
    private final Propagator propagator;

    @Override
    public Map<String, String> currentTrace() {
        var context = tracer.currentTraceContext().context();
        var headers = new LinkedHashMap<String, String>();
        if (context != null) {
            propagator.inject(context, headers, Map::put);
        }
        return headers;
    }

    @Override
    public void continueTrace(Map<String, String> headers, String name, Runnable send) {
        var span = propagator.extract(headers, Map::get).name(name).kind(Span.Kind.PRODUCER).start();
        try (var ignored = tracer.withSpan(span)) {
            send.run();
        } finally {
            span.end();
        }
    }
}
