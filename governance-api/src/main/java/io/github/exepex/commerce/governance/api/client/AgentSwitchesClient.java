package io.github.exepex.commerce.governance.api.client;

import io.github.exepex.commerce.governance.api.GovernancePaths;
import io.github.exepex.commerce.governance.api.dto.SwitchChange;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PutExchange;

/** The agents' kill switches, kept by the commerce MCP server: which agents are on, by agent id. */
public interface AgentSwitchesClient {

    @GetExchange(GovernancePaths.AGENT_SWITCHES)
    Map<String, Boolean> all();

    @PutExchange(GovernancePaths.AGENT_SWITCH)
    Map<String, Boolean> set(@PathVariable String agentId, @RequestBody SwitchChange change);
}
