package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** An operator switched the agent off: it may do nothing but hand work to a human. */
public class AgentSwitchedOffException extends GovernanceException {

    public AgentSwitchedOffException(String agentId) {
        super(HttpStatus.FORBIDDEN, ErrorMessages.AGENT_SWITCHED_OFF.formatted(agentId));
    }
}
