package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** The order has an open case that someone else may be working, so the agent must leave its money alone. */
public class OrderHandledByPeopleException extends GovernanceException {

    public OrderHandledByPeopleException(String holder) {
        super(HttpStatus.CONFLICT, ErrorMessages.ORDER_HANDLED_BY_PEOPLE.formatted(holder));
    }
}
