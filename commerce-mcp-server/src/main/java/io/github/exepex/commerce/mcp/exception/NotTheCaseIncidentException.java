package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The incident state reported for a case was read from another incident than the case's own. */
public class NotTheCaseIncidentException extends GovernanceException {

    public NotTheCaseIncidentException(String number, UUID caseId) {
        super(HttpStatus.CONFLICT, ErrorMessages.NOT_THE_CASE_INCIDENT.formatted(number, caseId));
    }
}
