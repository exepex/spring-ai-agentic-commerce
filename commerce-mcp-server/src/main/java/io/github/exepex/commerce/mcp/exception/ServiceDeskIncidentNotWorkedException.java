package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** A service-desk incident was reported as pending or resolved, but only one being worked is recorded as a case. */
public class ServiceDeskIncidentNotWorkedException extends CommerceException {

    public ServiceDeskIncidentNotWorkedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.SERVICE_DESK_INCIDENT_NOT_WORKED);
    }
}
