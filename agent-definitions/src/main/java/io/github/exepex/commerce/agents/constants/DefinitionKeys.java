package io.github.exepex.commerce.agents.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Where the agent definition files are and how they are laid out: the settings their YAML header holds. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DefinitionKeys {

    public static final String LOCATION = "classpath*:agents/*.md";
    /** The {@code ---} lines that enclose the header; only the first two split the file. */
    public static final String HEADER_SEPARATOR = "(?m)^---\\s*$";
    public static final String WINDOWS_LINE_BREAK = "\r\n";
    public static final String LINE_BREAK = "\n";

    public static final String ID = "id";
    public static final String MODEL = "model";
    public static final String EFFORT = "effort";
    public static final String TOOL_CALL_BUDGET = "tool-call-budget";
    public static final String CUSTOMER_SCOPED = "customer-scoped";
    public static final String SLACK_STEP = "slack-step";
    public static final String TOOLS = "tools";
    public static final String COMMERCE_TOOLS = "commerce";
    public static final String SLACK_TOOLS = "slack";
    public static final String SERVICENOW_TOOLS = "servicenow";
}
