package io.github.exepex.commerce.servicenow.security;

import io.github.exepex.commerce.servicenow.constants.AuthValues;
import io.github.exepex.commerce.servicenow.exception.UnauthenticatedToolCallException;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Reads the authenticated agent's id from a tool call's transport context. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallingAgent {

    public static String of(McpTransportContext context) {
        var agentId = context.get(AuthValues.AGENT_ID);
        if (agentId == null) {
            throw new UnauthenticatedToolCallException();
        }
        return agentId.toString();
    }
}
