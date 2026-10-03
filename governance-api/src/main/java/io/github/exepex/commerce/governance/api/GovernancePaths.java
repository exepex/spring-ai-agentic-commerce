package io.github.exepex.commerce.governance.api;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Where the commerce MCP server answers agents: the audit trail they report to, the kill switches, and the cases the
 * agent that works them keeps in step with ServiceNow. Every path under {@link #AGENT_API} needs the agent's token.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GovernancePaths {

    public static final String AGENT_API = "/api/agent/";
    public static final String TOOL_CALLS = AGENT_API + "tool-calls";
    public static final String DECISIONS = AGENT_API + "decisions";

    public static final String AGENT_CASES = AGENT_API + "cases";
    public static final String OUTGOING_CASES = AGENT_CASES + "/outgoing";
    public static final String CASE_INCIDENT = AGENT_CASES + "/{caseId}/incident";
    public static final String SERVICE_DESK_CASES = AGENT_CASES + "/service-desk";
    public static final String CASE_NOTE_SENT = AGENT_CASES + "/{caseId}/notes/{noteId}/sent";
    public static final String CASES_IN_SERVICENOW = AGENT_CASES + "/in-servicenow";
    public static final String CASE_INCIDENT_STATE = AGENT_CASES + "/{caseId}/incident-state";

    public static final String AGENT_SWITCHES = "/api/agent-switches";
    public static final String AGENT_SWITCH = AGENT_SWITCHES + "/{agentId}";

    /** The HTTP service group the clients belong to; its base URL is {@code spring.http.serviceclient.governance}. */
    public static final String CLIENT_GROUP = "governance";
}
