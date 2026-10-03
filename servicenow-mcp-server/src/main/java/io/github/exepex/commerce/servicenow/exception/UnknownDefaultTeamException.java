package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.ErrorMessages;

/** The default team is not one of the configured teams; the server refuses to start, as no incident could reach it. */
public class UnknownDefaultTeamException extends RuntimeException {

    public UnknownDefaultTeamException() {
        super(ErrorMessages.UNKNOWN_DEFAULT_TEAM);
    }
}
