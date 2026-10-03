package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** The tool works on one customer's orders, and the call named no customer. */
public class CustomerEmailRequiredException extends GovernanceException {

    public CustomerEmailRequiredException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.CUSTOMER_EMAIL_REQUIRED);
    }
}
