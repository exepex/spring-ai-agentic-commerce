package io.github.exepex.commerce.order.exception;

import org.springframework.http.HttpStatus;

/**
 * The catalog refused to take the order's stock out of the warehouse; the message is the catalog's own explanation.
 */
public class DispatchRefusedException extends OrderException {

    public DispatchRefusedException(String catalogDetail) {
        super(HttpStatus.CONFLICT, catalogDetail);
    }
}
