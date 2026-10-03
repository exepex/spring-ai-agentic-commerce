package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What the shop's tools record of each call in the audit trail, and what they answer the model with: the answer tells
 * the agent what happened and what to do next.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolMessages {

    public static final String SEARCHED_PRODUCTS = "Searched products for '%s'";
    public static final String LISTED_ORDERS = "Listed the orders of %s";
    public static final String LOOKED_UP_ORDER = "Looked up the order";
    public static final String TRACKED_SHIPMENT = "Tracked the shipment";
    public static final String PROPOSED_ORDER = "Proposed an order to %s";
    public static final String CANCELLED_ORDER = "Cancelled the order: %s";
    public static final String ASKED_TO_REFUND = "Asked to refund %s";
    public static final String NOTIFIED_CUSTOMER = "Notified the customer";
    public static final String HANDED_TO_SUPPORT = "Handed to the support team";

    public static final String NOT_PERMITTED = "Tool not permitted for this agent";
    public static final String SWITCHED_OFF = "Agent is switched off";
    /** A call that was refused or failed: what it was asked to do, and why it did not. */
    public static final String STOPPED_CALL = "%s: %s";

    public static final String CUSTOMER_NOTIFIED = "The customer was notified";
    public static final String CUSTOMER_ALREADY_NOTIFIED =
            "The customer was already told about this at %s; nothing was sent again";
    public static final String HANDED_OVER =
            "The support team has it as a ServiceNow incident and will take it from here";

    public static final String REFUND_EXECUTED = "Refunded %s %s.";
    public static final String REFUND_PENDING_APPROVAL = "This refund is above the approval limit and is waiting for a "
            + "human to approve it. Do not retry it. Tell the customer it is being reviewed.";
    public static final String REFUND_FAILED = "The refund did not go through: %s Retrying with the same idempotency key "
            + "is safe. If it keeps failing, hand it to a person instead of guessing.";
    public static final String REFUND_REJECTED = "A human rejected this refund: %s";

    /** A query is split into words at whitespace; a product matches when its text holds every word. */
    public static final String QUERY_WORD_SEPARATOR = "\\s+";
    public static final String SEARCHABLE_PRODUCT_TEXT = "%s %s %s";
    /** One line of an order, such as "2 x Headlamp", and how lines are joined. */
    public static final String ORDER_ITEM = "%s x %s";
    public static final String ORDER_ITEM_SEPARATOR = ", ";
}
