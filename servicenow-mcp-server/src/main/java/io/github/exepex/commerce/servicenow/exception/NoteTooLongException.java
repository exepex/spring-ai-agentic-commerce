package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;

/** A note is longer than ServiceNow takes in one work note. */
public class NoteTooLongException extends ToolRefusedException {

    public NoteTooLongException(int maxLength) {
        super(RefusalMessages.NOTE_TOO_LONG.formatted(maxLength));
    }
}
