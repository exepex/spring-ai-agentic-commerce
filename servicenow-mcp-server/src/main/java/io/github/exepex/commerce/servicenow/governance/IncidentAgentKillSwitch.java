package io.github.exepex.commerce.servicenow.governance;

import io.github.exepex.commerce.governance.api.client.AgentSwitchesClient;
import io.github.exepex.commerce.mcpserver.guard.KillSwitch;
import io.github.exepex.commerce.platform.logging.LogValues;
import io.github.exepex.commerce.servicenow.constants.ToolNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The agents' kill switches, as the commerce MCP server keeps them. A switched-off agent may still hand its incident
 * to a team, so no incident is left without an owner.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class IncidentAgentKillSwitch implements KillSwitch {

    private final AgentSwitchesClient switches;

    /** Fails closed: a switch that cannot be read counts as off. */
    @Override
    public boolean isSwitchedOn(String agentId) {
        try {
            return Boolean.TRUE.equals(switches.all().get(agentId));
        } catch (RuntimeException unreadable) {
            log.warn("Could not read the kill switch of {}; refusing its call", LogValues.safe(agentId), unreadable);
            return false;
        }
    }

    @Override
    public boolean worksWhileSwitchedOff(String tool) {
        return ToolNames.ASSIGN_TO_TEAM.equals(tool);
    }
}
