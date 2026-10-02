package io.github.exepex.commerce.agent;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wakes the incident agent for every ServiceNow incident claimed for it. Only started when the ServiceNow MCP server
 * is configured.
 */
@Component
class IncidentListener {

    private final IncidentAgent agent;
    private final JsonMapper jsonMapper;

    IncidentListener(IncidentAgent agent, JsonMapper jsonMapper) {
        this.agent = agent;
        this.jsonMapper = jsonMapper;
    }

    /**
     * An incident that could not be handed to anyone is delivered again every 15 seconds until the hand-off succeeds,
     * so no work is dropped however long the ServiceNow MCP server is down. Any other failure is not retried, so a
     * malformed event cannot block the ones behind it.
     */
    @Bean
    static DefaultErrorHandler handOffRetries() {
        DefaultErrorHandler retries = new DefaultErrorHandler(
                new FixedBackOff(Duration.ofSeconds(15).toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
        retries.defaultFalse();
        retries.addRetryableExceptions(HandOffFailedException.class);
        return retries;
    }

    @KafkaListener(topics = "${commerce.topics.incidents}", autoStartup = "#{'${commerce.servicenow.mcp-url:}' != ''}")
    void onIncident(String json) {
        JsonNode event = jsonMapper.readTree(json);
        agent.handleIncident(event.path("number").asString(), event.path("orderId").asString(""), json);
    }
}
