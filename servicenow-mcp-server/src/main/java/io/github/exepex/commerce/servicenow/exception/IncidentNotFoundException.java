package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;

/** No incident has the number the agent gave. */
public class IncidentNotFoundException extends ToolRefusedException {

    public IncidentNotFoundException(String number) {
        super(RefusalMessages.INCIDENT_NOT_FOUND.formatted(number));
    }
}
