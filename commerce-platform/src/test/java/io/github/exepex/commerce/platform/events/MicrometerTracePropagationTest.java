package io.github.exepex.commerce.platform.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class MicrometerTracePropagationTest {

    private final Tracer tracer = mock(Tracer.class, RETURNS_DEEP_STUBS);
    private final Propagator propagator = mock(Propagator.class);
    private final MicrometerTracePropagation tracing = new MicrometerTracePropagation(tracer, propagator);

    @Test
    void theTraceAnEventIsRaisedInIsWrittenWithIt() {
        var context = mock(TraceContext.class);
        when(tracer.currentTraceContext().context()).thenReturn(context);
        doAnswer(call -> {
            Propagator.Setter<Map<String, String>> setter = call.getArgument(2);
            setter.set(call.getArgument(1), "traceparent", "00-trace-span-01");
            return null;
        }).when(propagator).inject(eq(context), any(), any());

        assertThat(tracing.currentTrace()).containsEntry("traceparent", "00-trace-span-01");
    }

    @Test
    void anEventIsSentInsideTheTraceItWasRaisedInAndTheRelaySpanEnds() {
        var builder = mock(Span.Builder.class, RETURNS_SELF);
        var span = mock(Span.class);
        when(builder.start()).thenReturn(span);
        when(propagator.extract(eq(Map.of("traceparent", "00-trace-span-01")), any())).thenReturn(builder);
        var sent = new AtomicBoolean();

        tracing.continueTrace(Map.of("traceparent", "00-trace-span-01"), "order.events relay", () -> sent.set(true));

        assertThat(sent).isTrue();
        var order = inOrder(tracer, span);
        order.verify(tracer).withSpan(span);
        order.verify(span).end();
    }
}
