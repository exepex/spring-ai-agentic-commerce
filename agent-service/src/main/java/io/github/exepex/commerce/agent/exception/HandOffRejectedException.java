package io.github.exepex.commerce.agent.exception;

import io.github.exepex.commerce.agent.constants.ErrorMessages;

/** ServiceNow answered the hand-off with an error that is not a refusal, so it may take it on a later try. */
public class HandOffRejectedException extends HandOffFailedException {

    public HandOffRejectedException(String incidentNumber, String error) {
        super(ErrorMessages.HAND_OFF_REJECTED.formatted(incidentNumber, error), null);
    }
}
