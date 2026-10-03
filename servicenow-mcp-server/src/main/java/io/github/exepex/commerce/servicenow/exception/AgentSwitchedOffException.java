package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;

/** The agent's kill switch is off, or cannot be read; it may only hand its incident to a team. */
public class AgentSwitchedOffException extends ToolRefusedException {

    public AgentSwitchedOffException(String agentId) {
        super(RefusalMessages.AGENT_SWITCHED_OFF.formatted(agentId));
    }
}
