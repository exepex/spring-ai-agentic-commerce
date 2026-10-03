package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** There is no order proposal with that id. */
public class ProposalNotFoundException extends GovernanceException {

    public ProposalNotFoundException(UUID proposalId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.PROPOSAL_NOT_FOUND.formatted(proposalId));
    }
}
