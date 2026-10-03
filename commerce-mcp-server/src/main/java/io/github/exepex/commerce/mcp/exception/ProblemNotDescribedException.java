package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** A problem was raised without saying what it is. */
public class ProblemNotDescribedException extends CommerceException {

    public ProblemNotDescribedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.PROBLEM_NOT_DESCRIBED);
    }
}
