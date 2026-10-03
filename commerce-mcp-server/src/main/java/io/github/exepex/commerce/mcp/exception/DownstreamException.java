package io.github.exepex.commerce.mcp.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * A commerce service refused a request or could not be reached. The message is written for whoever acts on it next,
 * an agent or a person, and says whether retrying can help.
 */
@Getter
public abstract class DownstreamException extends RuntimeException {

    private final HttpStatusCode status;
    private final boolean retryable;
    private final transient Map<String, Object> properties;

    protected DownstreamException(HttpStatusCode status, String message, boolean retryable,
            Map<String, Object> properties, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.retryable = retryable;
        // A copy that keeps the order and any null values of the problem the service answered with.
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
