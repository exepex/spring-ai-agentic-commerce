package io.github.exepex.commerce.mcp.exception;

import java.util.Map;
import org.springframework.http.HttpStatusCode;

/** A commerce service turned the request down; asking again will not change its answer, which is the message. */
public class DownstreamRefusedException extends DownstreamException {

    public DownstreamRefusedException(HttpStatusCode status, String detail, Map<String, Object> properties,
            Throwable cause) {
        super(status, detail, false, properties, cause);
    }
}
