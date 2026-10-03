package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import java.util.Map;
import org.springframework.http.HttpStatusCode;

/** A commerce service answered with a server error; the same request may succeed once it is back. */
public class DownstreamUnavailableException extends DownstreamException {

    public DownstreamUnavailableException(String service, HttpStatusCode status, String detail,
            Map<String, Object> properties, Throwable cause) {
        super(status, ErrorMessages.DOWNSTREAM_UNAVAILABLE.formatted(service, detail), true, properties, cause);
    }
}
