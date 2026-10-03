package io.github.exepex.commerce.order.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Something the order service was asked to do cannot be done; the status says how a caller should take it, and the
 * properties are extra facts the answer carries for the caller.
 */
@Getter
public abstract class OrderException extends RuntimeException {

    private final HttpStatus status;
    private final transient Map<String, Object> properties;

    protected OrderException(HttpStatus status, String message) {
        this(status, message, Map.of(), null);
    }

    protected OrderException(HttpStatus status, String message, Throwable cause) {
        this(status, message, Map.of(), cause);
    }

    protected OrderException(HttpStatus status, String message, Map<String, Object> properties) {
        this(status, message, properties, null);
    }

    private OrderException(HttpStatus status, String message, Map<String, Object> properties, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.properties = Map.copyOf(properties);
    }
}
