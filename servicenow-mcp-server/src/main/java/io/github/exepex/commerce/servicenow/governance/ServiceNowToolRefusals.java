package io.github.exepex.commerce.servicenow.governance;

import io.github.exepex.commerce.mcpserver.guard.StoppedCallOutcome;
import io.github.exepex.commerce.mcpserver.guard.ToolRefusals;
import io.github.exepex.commerce.servicenow.exception.AgentSwitchedOffException;
import io.github.exepex.commerce.servicenow.exception.ToolNotPermittedException;
import io.github.exepex.commerce.servicenow.exception.ToolRefusedException;
import org.springframework.stereotype.Component;

/** How the ServiceNow tools refuse a call: in words that tell the agent what to do instead. */
@Component
class ServiceNowToolRefusals implements ToolRefusals {

    @Override
    public RuntimeException notPermitted(String agentId, String tool) {
        return new ToolNotPermittedException(agentId, tool);
    }

    @Override
    public RuntimeException switchedOff(String agentId) {
        return new AgentSwitchedOffException(agentId);
    }

    /** A refusal by the rules is denied; anything else, such as ServiceNow being down, failed. */
    @Override
    public StoppedCallOutcome outcomeOf(RuntimeException stopped) {
        return stopped instanceof ToolRefusedException ? StoppedCallOutcome.DENIED : StoppedCallOutcome.FAILED;
    }
}
