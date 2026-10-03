package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.mcp.governance.RefundRequest;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** Only a failed refund can be retried. */
public class RefundRequestNotFailedException extends CommerceException {

    public RefundRequestNotFailedException(RefundRequest.Status status) {
        super(HttpStatus.CONFLICT, ErrorMessages.REFUND_REQUEST_NOT_FAILED.formatted(status));
    }
}
