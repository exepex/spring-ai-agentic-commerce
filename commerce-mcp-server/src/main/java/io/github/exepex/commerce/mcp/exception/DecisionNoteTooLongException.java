package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** The decision note is longer than the database stores; it is refused before the decision moves any money. */
public class DecisionNoteTooLongException extends CommerceException {

    public DecisionNoteTooLongException(int maxLength) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.DECISION_NOTE_TOO_LONG.formatted(maxLength));
    }
}
