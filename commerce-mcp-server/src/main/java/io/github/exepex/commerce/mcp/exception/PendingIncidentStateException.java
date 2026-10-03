package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** An incident was reported as pending, which an incident in ServiceNow never is. */
public class PendingIncidentStateException extends GovernanceException {

    public PendingIncidentStateException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.PENDING_INCIDENT_STATE);
    }
}
