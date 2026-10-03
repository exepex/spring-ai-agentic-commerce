package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;

/** There is no agent by that id, so it has no kill switch to change. */
public class AgentNotFoundException extends CommerceException {

    public AgentNotFoundException(String agentId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.AGENT_NOT_FOUND.formatted(agentId));
    }
}
