package io.github.exepex.commerce.mcp.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * A governance rule stopped the request, or a commerce service it needed refused it. The message says why, for an
 * agent or a person to act on: a tool that throws it shows the model this message, and the API answers with it as the
 * problem's detail, with the status and any extra properties.
 */
@Getter
public abstract class GovernanceException extends RuntimeException {

    private final HttpStatusCode status;
    private final Map<String, Object> properties;

    protected GovernanceException(HttpStatusCode status, String message) {
        this(status, message, Map.of(), null);
    }

    protected GovernanceException(HttpStatusCode status, String message, Map<String, Object> properties,
            Throwable cause) {
        super(message, cause);
        this.status = status;
        // A copy that keeps the order and any null values the problem it came from had.
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
