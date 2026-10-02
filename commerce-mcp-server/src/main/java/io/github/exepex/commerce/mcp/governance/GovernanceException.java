package io.github.exepex.commerce.mcp.governance;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A governance rule stopped the request. The message says which rule, for an agent or a person to act on. */
public class GovernanceException extends ErrorResponseException {

    public GovernanceException(HttpStatus status, String message) {
        super(status, ProblemDetail.forStatusAndDetail(status, message), null);
    }

    @Override
    public String getMessage() {
        return getBody().getDetail();
    }
}
