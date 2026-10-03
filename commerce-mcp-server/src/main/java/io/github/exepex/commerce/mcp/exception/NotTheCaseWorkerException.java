package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** Only the agent that works cases may sync them with ServiceNow; another agent's token was presented. */
public class NotTheCaseWorkerException extends GovernanceException {

    public NotTheCaseWorkerException(String worker) {
        super(HttpStatus.FORBIDDEN, ErrorMessages.NOT_THE_CASE_WORKER.formatted(worker));
    }
}
