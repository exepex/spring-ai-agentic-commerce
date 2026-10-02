package io.github.exepex.commerce.agent;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wakes the incident agent for every ServiceNow incident claimed for it. Only started when the ServiceNow MCP server
 * is configured; a hand-off that fails is redelivered like a stock-out's.
 */
@Component
class IncidentListener {

    private final IncidentAgent agent;
    private final JsonMapper jsonMapper;

    IncidentListener(IncidentAgent agent, JsonMapper jsonMapper) {
        this.agent = agent;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "${commerce.topics.incidents}", autoStartup = "#{'${commerce.servicenow.mcp-url:}' != ''}")
    void onIncident(String json) {
        JsonNode event = jsonMapper.readTree(json);
        agent.handleIncident(event.path("number").asString(), event.path("orderId").asString(""), json);
    }
}
