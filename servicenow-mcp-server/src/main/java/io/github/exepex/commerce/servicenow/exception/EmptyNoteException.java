package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;

/** A work note, hand-over note or resolution was left empty. */
public class EmptyNoteException extends ToolRefusedException {

    public EmptyNoteException() {
        super(RefusalMessages.EMPTY_NOTE);
    }
}
