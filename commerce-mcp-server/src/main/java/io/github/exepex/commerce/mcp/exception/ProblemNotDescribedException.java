package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** A problem was raised without saying what it is. */
public class ProblemNotDescribedException extends GovernanceException {

    public ProblemNotDescribedException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, ErrorMessages.PROBLEM_NOT_DESCRIBED);
    }
}
