package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The ServiceNow tools by name, as agent definitions list them and the audit trail records them, and the action the
 * poller records as the agent.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolNames {

    public static final String GET_INCIDENT = "get_incident";
    public static final String ADD_WORK_NOTE = "add_work_note";
    public static final String LIST_TEAMS = "list_teams";
    /** Hands work to people; never blocked by the kill switch. */
    public static final String ASSIGN_TO_TEAM = "assign_to_team";
    public static final String RESOLVE_INCIDENT = "resolve_incident";
    /** Not a tool: the poller claiming an incident for the agent. */
    public static final String CLAIM_INCIDENT = "claim_incident";
}
