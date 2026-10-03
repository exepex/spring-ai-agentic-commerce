package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The actions the audit trail records, beside the tool calls recorded under the tool's name and the events other
 * services announce, recorded under the event's type.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuditActions {

    public static final String RAISE_CASE = "raise_case";
    public static final String OPEN_INCIDENT = "open_incident";
    public static final String FOLLOW_INCIDENT = "follow_incident";
    public static final String SWITCH_ON_AGENT = "switch_on_agent";
    public static final String SWITCH_OFF_AGENT = "switch_off_agent";
    public static final String NOTIFY_CUSTOMER = ToolNames.NOTIFY_CUSTOMER;
    public static final String CONFIRM_ORDER = "confirm_order";
    public static final String ISSUE_REFUND = ToolNames.ISSUE_REFUND;
    public static final String APPROVE_REFUND = "approve_refund";
    public static final String REJECT_REFUND = "reject_refund";
    public static final String REFUND_FAILED = "refund_failed";
    public static final String DECISION = "decision";
}
