package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The events the commerce services announce on Kafka that the MCP server acts on or describes. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventTypes {

    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_SHIPPED = "ORDER_SHIPPED";
    public static final String SHIPMENT_DELIVERED = "SHIPMENT_DELIVERED";
    public static final String SHIPMENT_DELIVERY_FAILED = "SHIPMENT_DELIVERY_FAILED";
    public static final String SHIPMENT_LOST = "SHIPMENT_LOST";
    public static final String REFUND_FAILED = "REFUND_FAILED";
    /** The stock-out topic carries no type; its events are recorded under this one. */
    public static final String STOCK_OUT = "STOCK_OUT";
}
