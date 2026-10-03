package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The shop's MCP tools, by the names agents call them and their definitions list them under. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolNames {

    public static final String SEARCH_PRODUCTS = "search_products";
    public static final String FIND_CUSTOMER_ORDERS = "find_customer_orders";
    public static final String GET_ORDER = "get_order";
    public static final String TRACK_SHIPMENT = "track_shipment";
    public static final String PROPOSE_ORDER = "propose_order";
    public static final String CANCEL_ORDER = "cancel_order";
    public static final String ISSUE_REFUND = "issue_refund";
    public static final String NOTIFY_CUSTOMER = "notify_customer";
    /** Handing work to a human; a switched-off agent may still call it. */
    public static final String ESCALATE_TO_HUMAN = "escalate_to_human";
}
