package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The MCP tools that agent-service treats specially, on the commerce and the ServiceNow MCP servers. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolNames {

    public static final String CANCEL_ORDER = "cancel_order";
    public static final String ISSUE_REFUND = "issue_refund";
    public static final String NOTIFY_CUSTOMER = "notify_customer";
    public static final String PROPOSE_ORDER = "propose_order";

    public static final String GET_INCIDENT = "get_incident";
    public static final String ADD_WORK_NOTE = "add_work_note";
    public static final String ASSIGN_TO_TEAM = "assign_to_team";
    public static final String RESOLVE_INCIDENT = "resolve_incident";
}
