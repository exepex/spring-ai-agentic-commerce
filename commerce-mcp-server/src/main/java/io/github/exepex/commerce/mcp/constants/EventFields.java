package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The fields of the Kafka events the MCP server reads. The topics are contracts, read as plain JSON. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventFields {

    public static final String TYPE = "type";
    public static final String EVENT_ID = "eventId";
    public static final String OCCURRED_AT = "occurredAt";
    public static final String ORDER_ID = "orderId";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String AMOUNT = "amount";
    public static final String CURRENCY = "currency";
    public static final String TRACKING_NUMBER = "trackingNumber";
    public static final String DELIVERY_PROBLEM = "deliveryProblem";
    public static final String SKU = "sku";
    public static final String ON_HAND = "onHand";
    public static final String RESERVED = "reserved";
    public static final String REASON = "reason";
    public static final String AFFECTED_ORDER_IDS = "affectedOrderIds";
    /** What a text field that an event leaves out reads as. */
    public static final String ABSENT = "";
}
