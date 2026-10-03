package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.mcp.governance.RefundRequest;
import org.springframework.http.HttpStatus;

/** Only a failed refund can be retried. */
public class RefundRequestNotFailedException extends GovernanceException {

    public RefundRequestNotFailedException(RefundRequest.Status status) {
        super(HttpStatus.CONFLICT, ErrorMessages.REFUND_REQUEST_NOT_FAILED.formatted(status));
    }
}
