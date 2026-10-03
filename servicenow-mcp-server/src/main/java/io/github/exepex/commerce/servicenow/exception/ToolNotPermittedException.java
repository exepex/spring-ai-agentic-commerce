package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;

/** The agent's definition does not list the tool among its ServiceNow tools. */
public class ToolNotPermittedException extends ToolRefusedException {

    public ToolNotPermittedException(String agentId, String tool) {
        super(RefusalMessages.TOOL_NOT_PERMITTED.formatted(agentId, tool));
    }
}
