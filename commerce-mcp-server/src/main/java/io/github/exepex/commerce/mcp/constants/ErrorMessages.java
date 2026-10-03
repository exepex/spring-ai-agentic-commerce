package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What the MCP server tells an agent or a person when it refuses a request or cannot do it. A tool's refusal reaches
 * the model word for word, so each one says what to do instead.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String AGENT_TOKEN_REQUIRED = "An agent bearer token is required";
    public static final String MISSING_AGENT_TOKEN = "No token is configured for agent %s";
    public static final String UNAUTHENTICATED_TOOL_CALL = "Tool called without an authenticated agent";
    public static final String TOOL_NOT_PERMITTED = "Agent %s is not permitted to call %s";
    public static final String AGENT_SWITCHED_OFF = "Agent %s is switched off. Stop, and hand any work that needs doing "
            + "to a human with " + ToolNames.ESCALATE_TO_HUMAN + ".";
    public static final String AGENT_NOT_FOUND = "There is no agent %s";
    public static final String NOT_THE_CUSTOMERS_ORDER = "Order %s does not belong to the customer in this conversation";
    public static final String CUSTOMER_EMAIL_REQUIRED = "A customer email is required";
    public static final String INVALID_ORDER_ID = "'%s' is not an order id";

    public static final String NOT_THE_CASE_WORKER = "Only the %s syncs cases with ServiceNow";
    public static final String ORDER_HANDLED_BY_PEOPLE = "This order is %s, who will finish it. Do not retry or refund it "
            + "another way; tell the customer a person is looking into it.";
    public static final String SERVICE_DESK_INCIDENT_NOT_WORKED =
            "A service-desk incident being worked is with the agent or a team";
    public static final String CASE_NOTE_NOT_FOUND = "Case %s has no note %s";
    public static final String PENDING_INCIDENT_STATE = "An incident in ServiceNow is not pending";
    public static final String NOT_THE_CASE_INCIDENT = "Incident %s is not the incident of case %s";
    public static final String CASE_NOT_FOUND = "Case %s does not exist";
    public static final String PROBLEM_NOT_DESCRIBED = "Say what the problem is";

    public static final String PROPOSAL_WITHOUT_LINES = "An order needs at least one line";
    public static final String DUPLICATE_PROPOSAL_LINE =
            "Each product can appear on only one line; put the whole quantity on that line.";
    public static final String PRODUCT_NOT_FOUND = "Product %s does not exist. Use " + ToolNames.SEARCH_PRODUCTS
            + " to find product ids.";
    public static final String INVALID_PRODUCT_ID = "'%s' is not a product id. Use " + ToolNames.SEARCH_PRODUCTS
            + " to find product ids.";
    public static final String INSUFFICIENT_STOCK = "Cannot order %s of %s: %s available.";
    public static final String PROPOSAL_NOT_FOUND = "Order proposal %s does not exist";

    public static final String REFUND_REASON_TOO_LONG =
            "A refund reason can be at most %s characters; say it in one sentence.";
    public static final String IDEMPOTENCY_KEY_TOO_LONG = "An idempotency key can be at most %s characters.";
    public static final String REFUND_EXCEEDS_REFUNDABLE =
            "A refund of %s %s exceeds the %s still refundable on this order.";
    public static final String IDEMPOTENCY_KEY_REUSED =
            "Idempotency key %s was already used for a different refund. Use a new key for a new refund.";
    public static final String IDEMPOTENCY_KEY_OF_ANOTHER_AGENT =
            "Idempotency key %s belongs to a refund another agent asked for. Do not reuse it.";
    public static final String REFUND_REQUEST_NOT_FAILED = "Refund request is %s, not failed";
    public static final String REFUND_REQUEST_NOT_PENDING = "Refund request is %s, not pending approval";
    public static final String DECISION_NOTE_TOO_LONG = "A decision note can be at most %s characters";
    public static final String REFUND_REQUEST_NOT_FOUND = "Refund request %s does not exist";

    public static final String DOWNSTREAM_UNAVAILABLE = "The %s is unavailable (%s). It is safe to retry later.";
    public static final String DOWNSTREAM_UNREACHABLE = "The %s could not be reached. It is safe to retry later.";
}
