package io.github.exepex.commerce.mcp.downstream;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A commerce service refused a request or could not be reached. The message is written for whoever acts on it next,
 * an agent or a person, and says whether retrying can help.
 */
public class DownstreamException extends ErrorResponseException {

    private final boolean retryable;

    DownstreamException(HttpStatusCode status, String message, boolean retryable, Throwable cause) {
        super(status, ProblemDetail.forStatusAndDetail(status, message), cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    @Override
    public String getMessage() {
        return getBody().getDetail();
    }
}
