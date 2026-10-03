package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** A commerce service did not answer at all; the same request may succeed once it is back. */
public class DownstreamUnreachableException extends DownstreamException {

    public DownstreamUnreachableException(String service, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, ErrorMessages.DOWNSTREAM_UNREACHABLE.formatted(service), true, Map.of(),
                cause);
    }
}
