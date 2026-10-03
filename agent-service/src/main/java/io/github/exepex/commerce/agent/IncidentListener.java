package io.github.exepex.commerce.agent;

import io.github.exepex.commerce.agent.constants.ConfigKeys;
import io.github.exepex.commerce.agent.constants.IncidentEventFields;
import io.github.exepex.commerce.agent.exception.HandOffFailedException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wakes the incident agent for every ServiceNow incident claimed for it. Only started when the ServiceNow MCP server
 * is configured.
 */
@Component
@RequiredArgsConstructor
class IncidentListener {

    private final IncidentAgent agent;
    private final JsonMapper jsonMapper;

    /**
     * An incident that could not be handed to anyone is delivered again every 15 seconds until the hand-off succeeds,
     * so no work is dropped however long the ServiceNow MCP server is down. Any other failure is not retried: the
     * event goes to the dead-letter topic at once, so a malformed event cannot block the ones behind it.
     */
    @Bean
    static DefaultErrorHandler handOffRetries(DeadLetterPublishingRecoverer deadLetters) {
        var retries = new DefaultErrorHandler(deadLetters,
                new FixedBackOff(Duration.ofSeconds(15).toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
        retries.defaultFalse();
        retries.addRetryableExceptions(HandOffFailedException.class);
        return retries;
    }

    @KafkaListener(topics = ConfigKeys.INCIDENTS_TOPIC, autoStartup = ConfigKeys.INCIDENT_LISTENER_AUTO_STARTUP)
    void onIncident(String json) {
        var event = jsonMapper.readTree(json);
        agent.handleIncident(event.path(IncidentEventFields.NUMBER).asString(),
                event.path(IncidentEventFields.ORDER_ID).asString(""), json);
    }
}
