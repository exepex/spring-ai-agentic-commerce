package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The MCP server's addresses: the MCP endpoint and the agent API, which need an agent's bearer token, and the API the
 * UI reads and acts through.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String MCP = "/mcp";
    public static final String AGENT_API = "/api/agent/";

    public static final String CASES = "/api/cases";
    public static final String AGENT_CASES = AGENT_API + "cases";
    public static final String OUTGOING_CASES = AGENT_CASES + "/outgoing";
    public static final String CASE_INCIDENT = AGENT_CASES + "/{caseId}/incident";
    public static final String SERVICE_DESK_CASES = AGENT_CASES + "/service-desk";
    public static final String CASE_NOTE_SENT = AGENT_CASES + "/{caseId}/notes/{noteId}/sent";
    public static final String CASES_IN_SERVICENOW = AGENT_CASES + "/in-servicenow";
    public static final String CASE_INCIDENT_STATE = AGENT_CASES + "/{caseId}/incident-state";

    public static final String ORDER_TIMELINE = "/api/orders/{orderId}/timeline";
    public static final String AUDIT_EVENTS = "/api/audit-events";
    public static final String REFUND_REQUESTS = "/api/refund-requests";
    public static final String APPROVE_REFUND = REFUND_REQUESTS + "/{requestId}/approve";
    public static final String REJECT_REFUND = REFUND_REQUESTS + "/{requestId}/reject";
    public static final String RETRY_REFUND = REFUND_REQUESTS + "/{requestId}/retry";
    public static final String ORDER_PROPOSAL = "/api/order-proposals/{proposalId}";
    public static final String CONFIRM_PROPOSAL = ORDER_PROPOSAL + "/confirm";
    public static final String NOTIFICATIONS = "/api/notifications";
    public static final String AGENT_SWITCHES = "/api/agent-switches";
    public static final String AGENT_SWITCH = AGENT_SWITCHES + "/{agentId}";
    public static final String TOOL_CALLS = AGENT_API + "tool-calls";
    public static final String DECISIONS = AGENT_API + "decisions";
}
