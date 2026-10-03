package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** An order was proposed without any line. */
public class ProposalWithoutLinesException extends GovernanceException {

    public ProposalWithoutLinesException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.PROPOSAL_WITHOUT_LINES);
    }
}
