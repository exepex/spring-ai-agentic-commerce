package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.exception.AgentSwitchedOffException;
import io.github.exepex.commerce.mcp.exception.DownstreamException;
import io.github.exepex.commerce.mcp.exception.ToolNotPermittedException;
import io.github.exepex.commerce.mcpserver.guard.StoppedCallOutcome;
import io.github.exepex.commerce.mcpserver.guard.ToolRefusals;
import io.github.exepex.commerce.platform.error.CommerceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** How the shop's tools refuse a call, in words the model can act on, and which stopped calls a rule denied. */
@Component
class CommerceToolRefusals implements ToolRefusals {

    @Override
    public RuntimeException notPermitted(String agentId, String tool) {
        return new ToolNotPermittedException(agentId, tool);
    }

    @Override
    public RuntimeException switchedOff(String agentId) {
        return new AgentSwitchedOffException(agentId);
    }

    /**
     * A rule that forbids the call denies it; anything else that stops it is a failure, a commerce service's refusal
     * too, whatever status that service answered with.
     */
    @Override
    public StoppedCallOutcome outcomeOf(RuntimeException stopped) {
        if (stopped instanceof DownstreamException) {
            return StoppedCallOutcome.FAILED;
        }
        return stopped instanceof CommerceException refused
                && refused.getStatus().value() == HttpStatus.FORBIDDEN.value()
                ? StoppedCallOutcome.DENIED
                : StoppedCallOutcome.FAILED;
    }
}
