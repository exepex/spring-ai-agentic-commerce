package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.mcp.governance.RefundRequest;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The refund no longer waits for approval: someone decided on it first. */
public class RefundRequestNotPendingException extends CommerceException {

    public RefundRequestNotPendingException(RefundRequest.Status status) {
        super(HttpStatus.CONFLICT, ErrorMessages.REFUND_REQUEST_NOT_PENDING.formatted(status));
    }
}
