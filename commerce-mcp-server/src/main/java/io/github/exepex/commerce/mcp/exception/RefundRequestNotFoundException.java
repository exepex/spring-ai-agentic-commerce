package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** There is no refund request with that id. */
public class RefundRequestNotFoundException extends CommerceException {

    public RefundRequestNotFoundException(UUID requestId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.REFUND_REQUEST_NOT_FOUND.formatted(requestId));
    }
}
