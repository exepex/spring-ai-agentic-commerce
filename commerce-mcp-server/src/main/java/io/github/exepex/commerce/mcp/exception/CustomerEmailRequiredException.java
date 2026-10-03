package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The tool works on one customer's orders, and the call named no customer. */
public class CustomerEmailRequiredException extends CommerceException {

    public CustomerEmailRequiredException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.CUSTOMER_EMAIL_REQUIRED);
    }
}
