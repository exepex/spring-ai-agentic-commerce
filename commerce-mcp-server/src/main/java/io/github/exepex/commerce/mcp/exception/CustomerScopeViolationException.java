package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** A customer-scoped agent asked about an order that is not the order of the customer it is talking to. */
public class CustomerScopeViolationException extends GovernanceException {

    public CustomerScopeViolationException(UUID orderId) {
        super(HttpStatus.FORBIDDEN, ErrorMessages.NOT_THE_CUSTOMERS_ORDER.formatted(orderId));
    }
}
