package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** The case has no such note. */
public class CaseNoteNotFoundException extends GovernanceException {

    public CaseNoteNotFoundException(UUID caseId, UUID noteId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.CASE_NOTE_NOT_FOUND.formatted(caseId, noteId));
    }
}
