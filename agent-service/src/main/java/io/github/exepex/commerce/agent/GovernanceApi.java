package io.github.exepex.commerce.agent;

import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The commerce MCP server's agent API, where agents record their decisions in the audit trail. */
@HttpExchange("/api/agent")
public interface GovernanceApi {

    record Decision(UUID orderId, String summary, String reasoning, String model, Long inputTokens, Long outputTokens,
            Long durationMillis) {}

    @PostExchange("/decisions")
    void recordDecision(@RequestHeader("Authorization") String authorization, @RequestBody Decision decision);
}
