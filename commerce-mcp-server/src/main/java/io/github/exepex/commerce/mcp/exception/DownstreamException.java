package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * A commerce service refused a request or could not be reached. The message is written for whoever acts on it next,
 * an agent or a person, and says whether retrying can help. The problem detail the service answered with keeps its
 * status and extra properties.
 */
@Getter
public class DownstreamException extends CommerceException {

    private final boolean retryable;

    private DownstreamException(HttpStatusCode status, String message, boolean retryable,
            Map<String, Object> properties, Throwable cause) {
        super(status, message, properties, cause);
        this.retryable = retryable;
    }

    /** The service turned the request down; asking again will not change its answer, which is the message. */
    public static DownstreamException refused(HttpStatusCode status, String detail, Map<String, Object> properties,
            Throwable cause) {
        return new DownstreamException(status, detail, false, properties, cause);
    }

    /** The service answered with a server error; the same request may succeed once it is back. */
    public static DownstreamException unavailable(String service, HttpStatusCode status, String detail,
            Map<String, Object> properties, Throwable cause) {
        return new DownstreamException(status, ErrorMessages.DOWNSTREAM_UNAVAILABLE.formatted(service, detail), true,
                properties, cause);
    }

    /** The service did not answer at all; the same request may succeed once it is back. */
    public static DownstreamException unreachable(String service, Throwable cause) {
        return new DownstreamException(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorMessages.DOWNSTREAM_UNREACHABLE.formatted(service), true, Map.of(), cause);
    }
}
