package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The case has no such note. */
public class CaseNoteNotFoundException extends CommerceException {

    public CaseNoteNotFoundException(UUID caseId, UUID noteId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.CASE_NOTE_NOT_FOUND.formatted(caseId, noteId));
    }
}
