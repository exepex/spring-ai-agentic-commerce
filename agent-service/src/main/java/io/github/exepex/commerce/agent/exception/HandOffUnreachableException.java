package io.github.exepex.commerce.agent.exception;

import io.github.exepex.commerce.agent.constants.ErrorMessages;

/** ServiceNow or its MCP server could not be reached to hand the incident to a team. */
public class HandOffUnreachableException extends HandOffFailedException {

    public HandOffUnreachableException(String incidentNumber, String note, Throwable cause) {
        super(ErrorMessages.HAND_OFF_UNREACHABLE.formatted(incidentNumber, note), cause);
    }
}
