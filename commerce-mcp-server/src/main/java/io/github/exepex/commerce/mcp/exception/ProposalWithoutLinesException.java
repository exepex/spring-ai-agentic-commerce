package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** An order was proposed without any line. */
public class ProposalWithoutLinesException extends CommerceException {

    public ProposalWithoutLinesException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.PROPOSAL_WITHOUT_LINES);
    }
}
