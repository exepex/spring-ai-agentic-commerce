package io.github.exepex.commerce.platform.events;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Trace headers as the outbox stores them: one {@code name=value} per line. Header values hold no line breaks. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TraceHeaders {

    private static final String LINE = "\n";
    private static final String NAME_VALUE = "=";

    static String encode(Map<String, String> headers) {
        if (headers.isEmpty()) {
            return null;
        }
        return headers.entrySet().stream()
                .map(header -> header.getKey() + NAME_VALUE + header.getValue())
                .collect(Collectors.joining(LINE));
    }

    static Map<String, String> decode(String encoded) {
        var headers = new LinkedHashMap<String, String>();
        if (encoded != null) {
            for (var line : encoded.split(LINE)) {
                var separator = line.indexOf(NAME_VALUE);
                if (separator > 0) {
                    headers.put(line.substring(0, separator), line.substring(separator + 1));
                }
            }
        }
        return headers;
    }
}
