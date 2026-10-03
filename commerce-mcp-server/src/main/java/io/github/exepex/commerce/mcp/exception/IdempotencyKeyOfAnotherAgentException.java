package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/**
 * The idempotency key names a refund another agent asked for: repeating it would act, and be audited, as that
 * agent.
 */
public class IdempotencyKeyOfAnotherAgentException extends GovernanceException {

    public IdempotencyKeyOfAnotherAgentException(String idempotencyKey) {
        super(HttpStatus.CONFLICT, ErrorMessages.IDEMPOTENCY_KEY_OF_ANOTHER_AGENT.formatted(idempotencyKey));
    }
}
