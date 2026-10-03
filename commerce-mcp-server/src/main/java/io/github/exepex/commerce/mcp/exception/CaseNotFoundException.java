package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** There is no case with that id. */
public class CaseNotFoundException extends CommerceException {

    public CaseNotFoundException(UUID caseId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.CASE_NOT_FOUND.formatted(caseId));
    }
}
