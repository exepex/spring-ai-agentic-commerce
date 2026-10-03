package io.github.exepex.commerce.mcp.exception;

import io.github.exepex.commerce.mcp.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** The tool is not on the agent's allowlist, which its definition sets. */
public class ToolNotPermittedException extends GovernanceException {

    public ToolNotPermittedException(String agentId, String tool) {
        super(HttpStatus.FORBIDDEN, ErrorMessages.TOOL_NOT_PERMITTED.formatted(agentId, tool));
    }
}
