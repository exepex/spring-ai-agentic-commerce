package io.github.exepex.commerce.platform.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * Something a service was asked to do cannot be done. Every service's own failures extend it, so they are answered
 * the same way everywhere: as an RFC 9457 problem detail with this status, the message as its detail, and the extra
 * properties a caller may need (such as the order a declined payment belongs to).
 */
@Getter
public abstract class CommerceException extends RuntimeException {

    private final HttpStatusCode status;
    private final transient Map<String, Object> properties;

    protected CommerceException(HttpStatusCode status, String message) {
        this(status, message, Map.of(), null);
    }

    protected CommerceException(HttpStatusCode status, String message, Throwable cause) {
        this(status, message, Map.of(), cause);
    }

    protected CommerceException(HttpStatusCode status, String message, Map<String, Object> properties) {
        this(status, message, properties, null);
    }

    protected CommerceException(HttpStatusCode status, String message, Map<String, Object> properties,
            Throwable cause) {
        super(message, cause);
        this.status = status;
        // Kept in order and with any null values, as the problem the properties may come from had them.
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
