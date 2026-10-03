package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;

/** What the agent gave as an incident number is not one, so it never reaches a query. */
public class InvalidIncidentNumberException extends ToolRefusedException {

    public InvalidIncidentNumberException(String number) {
        super(RefusalMessages.NOT_AN_INCIDENT_NUMBER.formatted(number));
    }
}
