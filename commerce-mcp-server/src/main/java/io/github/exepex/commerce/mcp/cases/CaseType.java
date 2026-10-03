package io.github.exepex.commerce.mcp.cases;

import io.github.exepex.commerce.mcp.constants.CaseWording;
import java.util.UUID;

/**
 * The kinds of problem the shop opens a case for. The type leads the incident's short description, in brackets, so
 * the incident agent and the support teams see at once what kind of problem it is.
 */
public enum CaseType {
    STOCK_OUT(CaseWording.STOCK_OUT),
    DELIVERY_FAILED(CaseWording.DELIVERY_FAILED),
    PARCEL_LOST(CaseWording.PARCEL_LOST),
    REFUND_FAILED(CaseWording.REFUND_FAILED),
    /** An agent handed over something it could not or should not handle itself. */
    HANDOFF(CaseWording.HANDOFF),
    /**
     * An incident the service desk raised in ServiceNow about an order. The shop does not open it; it records it, so
     * agents leave the order's money to whoever works it. Its title is the incident's own short description.
     */
    SERVICE_DESK(CaseWording.SERVICE_DESK);

    private final String problem;

    CaseType(String problem) {
        this.problem = problem;
    }

    /** The incident's short description, such as "[STOCK_OUT] Order 1a2b3c4d can no longer be fulfilled: …". */
    String titleFor(UUID orderId) {
        var subject = orderId == null
                ? CaseWording.REQUEST_SUBJECT
                : CaseWording.ORDER_SUBJECT.formatted(orderId.toString().substring(0, 8));
        return CaseWording.TITLE.formatted(name(), subject, problem);
    }
}
