package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.ApiPaths;
import io.github.exepex.commerce.agent.dto.Decision;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The commerce MCP server's agent API, where agents record their decisions in the audit trail. */
@HttpExchange(ApiPaths.GOVERNANCE_AGENT_API)
public interface GovernanceApi {

    @PostExchange(ApiPaths.DECISIONS)
    void recordDecision(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody Decision decision);
}
