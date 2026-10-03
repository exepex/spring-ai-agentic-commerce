package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * How the audit trail words what happened to refunds, notifications, proposals and switches, and what the commerce
 * services announced, as an order's timeline shows it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuditSummaries {

    /** An amount with its currency, such as "39.50 EUR". */
    public static final String AMOUNT = "%s %s";
    public static final String IDEMPOTENCY_KEY = "Idempotency key %s";
    public static final String REFUND_AWAITS_APPROVAL = "Refund of %s %s takes this order's refunds to %s, above the %s "
            + "limit, and waits for a human to approve it";
    public static final String REFUND_APPROVED = "Approved a refund of %s %s";
    public static final String REFUND_REJECTED = "Rejected a refund of %s %s";
    public static final String REFUND_FAILED = "Refund of %s failed: %s";
    public static final String REFUNDED = "Refunded %s (%s)";
    public static final String REFUND_TAKEN_AFTER_FAILURE = "The payment service took the refund of %s, but the card "
            + "processor had already reported it failed";
    public static final String REFUND_FAILED_AT_PROCESSOR = "The card processor reported the refund of %s %s failed "
            + "after accepting it; no money was returned.";
    public static final String REFUND_FAILED_CASE = "%s Idempotency key %s. The customer must be refunded another way.";

    public static final String NOTIFIED = "Notified %s";
    public static final String ALREADY_NOTIFIED = "Already notified %s with key %s; nothing sent again";

    public static final String ORDER_CONFIRMED_BY_CUSTOMER = "Customer confirmed the proposed order and paid %s %s";
    public static final String ORDER_NOT_PLACED = "Order could not be placed: %s";
    public static final String PROPOSAL = "Proposal %s";

    public static final String SWITCHED_ON = "Switched on %s";
    public static final String SWITCHED_OFF = "Switched off %s";

    /** The model and token usage behind an agent's decision, put ahead of its reasoning. */
    public static final String DECISION_USAGE = "Model %s, %s input and %s output tokens, %s ms";
    public static final String DECISION_DETAILS = "%s\n\n%s";

    public static final String ORDER_CONFIRMED = "Order confirmed and paid; shipping notified";
    public static final String ORDER_CANCELLED = "Order cancelled; stock released and shipment cancelled";
    public static final String ORDER_SHIPPED =
            "Order shipped: its stock left the warehouse and the parcel is with the carrier";
    public static final String OTHER_ORDER_EVENT = "Order event %s";
    public static final String PARCEL = "Parcel %s";
    public static final String PARCEL_DELIVERED = "%s delivered to the customer";
    public static final String PARCEL_NOT_DELIVERED = "%s could not be delivered: %s";
    public static final String PARCEL_LOST = "%s lost by the carrier: %s";
    public static final String OTHER_SHIPMENT_EVENT = "Shipment event %s";
    public static final String PARCEL_NOT_RECEIVED = "%s. The customer did not receive order %s.";
    public static final String STOCK_OUT = "Stock-out on %s: %s on hand for %s reserved (%s). This order can no longer "
            + "be fulfilled as placed.";
}
