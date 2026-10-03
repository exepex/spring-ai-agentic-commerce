package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.governance.GovernanceException;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

/** Reads what a model passed to a tool, and refuses what cannot be what the tool needs, with a message it can act on. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ToolArguments {

    static String requireCustomer(String customerEmail) {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "A customer email is required");
        }
        return customerEmail;
    }

    static UUID parseOrderId(String orderId) {
        try {
            return UUID.fromString(orderId);
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new GovernanceException(HttpStatus.UNPROCESSABLE_CONTENT, "'" + orderId + "' is not an order id");
        }
    }

    /** An order id the model may leave out, for a problem that is about no single order. */
    static UUID optionalOrderId(String orderId) {
        return orderId == null || orderId.isBlank() ? null : parseOrderId(orderId);
    }

    /** A key the model may leave out; a blank one counts as none. */
    static String optionalKey(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey;
    }
}
