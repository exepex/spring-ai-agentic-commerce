package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The MCP endpoint this server serves, and the commerce MCP server's governance API it calls. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiPaths {

    public static final String MCP = "/mcp";

    public static final String AGENT_SWITCHES = "/api/agent-switches";
    public static final String TOOL_CALLS = "/api/agent/tool-calls";
    public static final String CASES = "/api/agent/cases";
    public static final String OUTGOING_CASES = CASES + "/outgoing";
    public static final String CASES_IN_SERVICENOW = CASES + "/in-servicenow";
    public static final String SERVICE_DESK_CASES = CASES + "/service-desk";
    public static final String CASE_INCIDENT = CASES + "/{caseId}/incident";
    public static final String CASE_INCIDENT_STATE = CASES + "/{caseId}/incident-state";
    public static final String NOTE_SENT = CASES + "/{caseId}/notes/{noteId}/sent";
}
