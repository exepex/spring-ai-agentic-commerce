package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The addresses of the API the UI reads and acts through. The MCP endpoint and the agent API, which need an agent's
 * bearer token, and the kill switches are in {@code GovernancePaths}, the contract the agents' services share.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String CASES = "/api/cases";
    public static final String ORDER_TIMELINE = "/api/orders/{orderId}/timeline";
    public static final String AUDIT_EVENTS = "/api/audit-events";
    public static final String REFUND_REQUESTS = "/api/refund-requests";
    public static final String APPROVE_REFUND = REFUND_REQUESTS + "/{requestId}/approve";
    public static final String REJECT_REFUND = REFUND_REQUESTS + "/{requestId}/reject";
    public static final String RETRY_REFUND = REFUND_REQUESTS + "/{requestId}/retry";
    public static final String ORDER_PROPOSAL = "/api/order-proposals/{proposalId}";
    public static final String CONFIRM_PROPOSAL = ORDER_PROPOSAL + "/confirm";
    public static final String NOTIFICATIONS = "/api/notifications";
}
