package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How the shop's MCP tools and their parameters are described to the model, which picks and fills them by these. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolDescriptions {

    public static final String SEARCH_PRODUCTS = """
            Search the catalog. Returns matching products with their id, price and how many are available. \
            Leave the query empty to list every product.""";
    public static final String FIND_CUSTOMER_ORDERS = "List a customer's orders, newest first.";
    public static final String GET_ORDER = """
            Get everything about one order: its lines and status, the payment (paid, refunded and still refundable), \
            the shipment, any refunds already requested for it (with their idempotency keys), and when the customer \
            was notified about it.""";
    public static final String TRACK_SHIPMENT = """
            Get an order's shipment: its tracking number, status, estimated delivery date, when it shipped and was \
            delivered, and what went wrong if it was not. The status is PREPARING, SHIPPED, DELIVERED, \
            DELIVERY_FAILED, LOST or CANCELLED.""";
    public static final String PROPOSE_ORDER = """
            Put together an order for the customer to confirm. Nothing is charged or reserved: the customer sees the \
            proposal in the chat and must press "Confirm and pay" themselves. Use product ids from search_products.""";
    public static final String CANCEL_ORDER = """
            Cancel an order: its stock goes back to the shelf and its shipment is cancelled. Cancelling does not \
            refund the payment; use issue_refund for that. Cancelling an already cancelled order changes nothing. An \
            order that has shipped can no longer be cancelled.""";
    public static final String ISSUE_REFUND = """
            Refund part or all of an order's payment. Refunds above the approval limit are not paid out straight away: \
            they wait for a human, and the result says so. Choose an idempotency key for each new refund, for example \
            "refund-<order id>-1". If a refund fails because a service is down, retrying with the SAME key is safe and \
            never pays out twice.""";
    public static final String NOTIFY_CUSTOMER = """
            Send the customer of an order a short message, for example to explain a cancellation and refund. With an \
            idempotency key the message is sent once: sending again with the same key sends nothing.""";
    public static final String ESCALATE_TO_HUMAN = """
            Hand a problem to the support team when you cannot or should not resolve it yourself, for example when a \
            service keeps failing. It becomes a ServiceNow incident: the incident agent looks into it first and passes \
            it to the right team when a person is needed. Say what happened, what you already did, and what you \
            recommend.""";

    public static final String CUSTOMER_EMAIL =
            "The customer's email. In a customer conversation the application fills it in.";
    public static final String ORDER_ID = "The order id";
    public static final String PRODUCT_QUERY = "Words to look for in the product name, SKU or description";
    public static final String PROPOSED_LINES = "The products and quantities to order";
    public static final String CANCELLATION_REASON = "Why the order is cancelled, in a sentence";
    public static final String REFUND_AMOUNT = "The amount to refund, in the order's currency";
    public static final String REFUND_REASON = "Why the customer is refunded, in a sentence";
    public static final String REFUND_KEY = "A key that identifies this refund; reuse it only to retry the same refund";
    public static final String INCIDENT_NUMBER =
            "The incident the refund is for; set by the agent platform, not by the model";
    public static final String MESSAGE = "The message, written to the customer";
    public static final String MESSAGE_KEY =
            "A key that identifies this message; reuse it only to repeat the same message";
    public static final String ESCALATED_ORDER_ID = "The order id, if the problem is about one order";
    public static final String ESCALATION_SUMMARY = "What happened, what you already did, and what you recommend";
}
