package io.github.exepex.commerce.shipping.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Something the shipping service was asked to do cannot be done; the status says how a caller should take it. */
@Getter
public abstract class ShippingException extends RuntimeException {

    private final HttpStatus status;

    protected ShippingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
