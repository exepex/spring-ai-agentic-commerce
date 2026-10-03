package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How agent-service connects to the MCP servers and reads what their tools exchange. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class McpValues {

    public static final String SLACK = "slack";
    public static final String SERVICENOW = "servicenow";
    /** A tool of another server than the commerce MCP server is shown with the server's name before it. */
    public static final String SLACK_TOOL_PREFIX = SLACK + ":";
    public static final String SERVICENOW_TOOL_PREFIX = SERVICENOW + ":";

    public static final String CLIENT_NAME_PREFIX = "agent-service/";
    public static final String CLIENT_VERSION = "1.0.0";
    /** Where a run's {@code ToolRun} is kept in Spring AI's tool context. */
    public static final String TOOL_RUN_CONTEXT_KEY = "commerce.toolRun";

    public static final String CONTENT_TEXT = "text";
    public static final String NO_ARGUMENTS = "{}";
    public static final String SCHEMA_PROPERTIES = "properties";
    public static final String SCHEMA_REQUIRED = "required";
}
