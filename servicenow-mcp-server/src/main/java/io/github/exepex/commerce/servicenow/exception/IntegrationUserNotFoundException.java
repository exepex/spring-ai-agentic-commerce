package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.ErrorMessages;

/** The instance has no user with the integration user's name, so nothing can be claimed for the agent. */
public class IntegrationUserNotFoundException extends ServiceNowMcpException {

    public IntegrationUserNotFoundException(String username) {
        super(ErrorMessages.INTEGRATION_USER_NOT_FOUND.formatted(username));
    }
}
