package io.github.exepex.commerce.mcp.cases;

import java.util.UUID;

/**
 * The kinds of problem the shop opens a case for. The type leads the incident's short description, in brackets, so
 * the incident agent and the support teams see at once what kind of problem it is.
 */
public enum CaseType {
    STOCK_OUT("can no longer be fulfilled: stock ran out after it was placed"),
    DELIVERY_FAILED("could not be delivered"),
    PARCEL_LOST("was lost by the carrier"),
    REFUND_FAILED("has a refund that failed at the card processor"),
    /** An agent handed over something it could not or should not handle itself. */
    HANDOFF("needs a person"),
    /**
     * An incident the service desk raised in ServiceNow about an order. The shop does not open it; it records it, so
     * agents leave the order's money to whoever works it. Its title is the incident's own short description.
     */
    SERVICE_DESK("has an incident the service desk raised");

    private final String problem;

    CaseType(String problem) {
        this.problem = problem;
    }

    /** The incident's short description, such as "[STOCK_OUT] Order 1a2b3c4d can no longer be fulfilled: …". */
    String titleFor(UUID orderId) {
        return "[" + name() + "] " + (orderId == null ? "A request " : "Order " + orderId.toString().substring(0, 8) + " ")
                + problem;
    }
}
