package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.ApiPaths;
import io.github.exepex.commerce.agent.dto.Change;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;

/** The agents' kill switches, kept by the commerce MCP server: each agent's id and whether it is on. */
@HttpExchange(ApiPaths.AGENT_SWITCHES)
public interface AgentSwitchesApi {

    @GetExchange
    Map<String, Boolean> all();

    @PutExchange(ApiPaths.AGENT_ID)
    Map<String, Boolean> set(@PathVariable String agentId, @RequestBody Change change);
}
