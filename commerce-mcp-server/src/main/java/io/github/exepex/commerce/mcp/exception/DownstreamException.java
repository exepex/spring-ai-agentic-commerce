package io.github.exepex.commerce.mcp.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * A commerce service refused a request or could not be reached. The message is written for whoever acts on it next,
 * an agent or a person, and says whether retrying can help.
 */
@Getter
public abstract class DownstreamException extends GovernanceException {

    private final boolean retryable;

    protected DownstreamException(HttpStatusCode status, String message, boolean retryable,
            Map<String, Object> properties, Throwable cause) {
        super(status, message, properties, cause);
        this.retryable = retryable;
    }
}
