package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.constants.ToolNames;
import io.github.exepex.commerce.mcp.governance.AgentSwitches;
import io.github.exepex.commerce.mcpserver.guard.KillSwitch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The kill switch every tool call is checked against: the agent's switch as {@link AgentSwitches} keeps it. A
 * switched-off agent may still hand work to a human, so nothing is left without someone handling it.
 */
@Component
@RequiredArgsConstructor
class AgentKillSwitch implements KillSwitch {

    private final AgentSwitches switches;

    @Override
    public boolean isSwitchedOn(String agentId) {
        return switches.isEnabled(agentId);
    }

    @Override
    public boolean worksWhileSwitchedOff(String tool) {
        return ToolNames.ESCALATE_TO_HUMAN.equals(tool);
    }
}
