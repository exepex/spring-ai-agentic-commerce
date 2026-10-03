package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** An order was proposed with the same product on more than one line. */
public class DuplicateProposalLineException extends CommerceException {

    public DuplicateProposalLineException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.DUPLICATE_PROPOSAL_LINE);
    }
}
