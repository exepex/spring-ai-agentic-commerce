package io.github.exepex.commerce.platform.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraceHeadersTest {

    @Test
    void theHeadersOfATraceSurviveTheOutbox() {
        var headers = new LinkedHashMap<String, String>();
        headers.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        headers.put("tracestate", "vendor=a=b");

        assertThat(TraceHeaders.decode(TraceHeaders.encode(headers))).isEqualTo(headers);
    }

    @Test
    void anEventRaisedOutsideATraceStoresNoHeaders() {
        assertThat(TraceHeaders.encode(Map.of())).isNull();
        assertThat(TraceHeaders.decode(null)).isEmpty();
    }
}
