package io.github.exepex.commerce.mcp.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * How cases are worded: their incident's title in ServiceNow, who has them, and what happens to them on the order's
 * timeline.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CaseWording {

    /** The incident's short description: the case type in brackets, what it is about, and the problem. */
    public static final String TITLE = "[%s] %s%s";
    public static final String ORDER_SUBJECT = "Order %s ";
    public static final String REQUEST_SUBJECT = "A request ";
    public static final String STOCK_OUT = "can no longer be fulfilled: stock ran out after it was placed";
    public static final String DELIVERY_FAILED = "could not be delivered";
    public static final String PARCEL_LOST = "was lost by the carrier";
    public static final String REFUND_FAILED = "has a refund that failed at the card processor";
    public static final String HANDOFF = "needs a person";
    public static final String SERVICE_DESK = "has an incident the service desk raised";
    public static final String SERVICE_DESK_DESCRIPTION = "Raised by the service desk in ServiceNow as %s.";
    /** Marks a description cut short to fit ServiceNow. */
    public static final String TRUNCATED = "…";

    public static final String NO_INCIDENT = "";
    public static final String INCIDENT = " (%s)";
    public static final String WITH_TEAM_HOLDER = "with the %s team in ServiceNow%s";
    public static final String SUPPORT_TEAM_HOLDER = "an open %s case%s that the support team handles";

    public static final String REOPENED_AND = " was reopened and";
    public static final String NOT_REOPENED = "";
    public static final String WITH_AGENT = "%s is with the incident agent";
    public static final String ASSIGNED = "%s is assigned to %s";
    public static final String RESOLVED = "%s is resolved";
    public static final String SERVICE_DESK_RESOLVED = "%s is now about this order and is resolved";
    public static final String SERVICE_DESK_REOPENED = " was reopened";
    public static final String SERVICE_DESK_NOW_ABOUT_ORDER = " is now about this order";
    public static final String SERVICE_DESK_OPEN = "%s%s; agents leave its money to whoever works it";

    public static final String ADDED_TO_OPEN_CASE = "Added to the open %s case%s";
    public static final String OPENED_CASE =
            "Opened a %s case; it goes to ServiceNow as an incident for the incident agent";
    public static final String OPENED_INCIDENT = "Opened ServiceNow incident %s for the %s case";
    public static final String OPEN_CASE_TAKES_PROBLEM = "The order's open %s case%s still takes what is raised again.";
    public static final String RAISED_AGAIN =
            "Raised again after %s was resolved; what was raised follows as work notes.";
    public static final String SERVICE_DESK_RAISED = "The service desk raised incident %s about this order; agents "
            + "leave its money to whoever works it";
    public static final String NO_LONGER_NAMES_ORDER = "%s no longer names this order, so its case is closed and agents "
            + "may handle the order's money again";
    public static final String NOW_ABOUT_ORDER = "%s is now about order %s";

    /** The lock key of an order's case of one type. */
    public static final String PROBLEM_LOCK_KEY = "%s/%s";
}
