package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.governance.GovernanceApi;
import io.github.exepex.commerce.servicenow.security.AgentRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Feeds ServiceNow incidents to the incident agent. Each new, unassigned incident in the agent's group is claimed by
 * assigning it to the integration user, then announced on Kafka: a claimed incident is no longer new, so it is never
 * announced twice. If Kafka does not take the announcement, the claim is given back, so the next poll claims and
 * announces the incident again. ServiceNow offers no outbound call without a public URL, so the demo asks every poll interval.
 *
 * <p>The Table API has no conditional update, so each incident is read again right before it is claimed or handed
 * over, and left alone if a person took it or changed it meanwhile. That narrows the race with a person to the time
 * between that read and the update.
 *
 * <p>A claimed incident the agent has not finished within {@code commerce.servicenow.stale-after}, because
 * agent-service was down or its run stopped, is handed to the default team, so no incident waits for an agent that
 * is not coming.
 */
@Component
public class IncidentPoller {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentPoller.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final ServiceNowClient serviceNow;
    private final ServiceNowProperties properties;
    private final KafkaTemplate<String, IncidentEvent> kafka;
    private final String topic;
    private final GovernanceApi governance;
    private final AgentRegistry agents;
    private final Clock clock;

    IncidentPoller(ServiceNowClient serviceNow, ServiceNowProperties properties, KafkaTemplate<String, IncidentEvent> kafka,
            @Value("${commerce.topics.incidents}") String topic, GovernanceApi governance, AgentRegistry agents,
            Clock clock) {
        this.serviceNow = serviceNow;
        this.properties = properties;
        this.kafka = kafka;
        this.topic = topic;
        this.governance = governance;
        this.agents = agents;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${commerce.servicenow.poll-interval}",
            initialDelayString = "${commerce.servicenow.poll-interval}")
    public void poll() {
        if (!properties.isConfigured()) {
            return;
        }
        try {
            handOverStaleClaims();
            claimNewIncidents();
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Could not poll ServiceNow for incidents; trying again next time", unavailable);
        }
    }

    private void claimNewIncidents() {
        for (ServiceNowClient.Incident found : serviceNow.findNewForAgent()) {
            ServiceNowClient.Incident incident = serviceNow.findByNumber(found.number()).orElse(null);
            if (incident == null || incident.isAssigned() || !ServiceNowClient.STATE_NEW.equals(incident.state())) {
                continue;
            }
            Map<String, String> claim = new LinkedHashMap<>();
            claim.put("assigned_to", serviceNow.integrationUserSysId());
            claim.put("state", ServiceNowClient.STATE_IN_PROGRESS);
            claim.put("work_notes", "Picked up by the " + properties.agent() + ".");
            serviceNow.update(incident.sysId(), claim);
            if (!announce(incident)) {
                Map<String, String> release = new LinkedHashMap<>();
                release.put("assigned_to", "");
                release.put("state", ServiceNowClient.STATE_NEW);
                serviceNow.update(incident.sysId(), release);
                continue;
            }
            record("claim_incident", "Claimed " + incident.number() + ": " + incident.shortDescription());
        }
    }

    /** Whether Kafka took the announcement. */
    private boolean announce(ServiceNowClient.Incident incident) {
        try {
            kafka.send(topic, incident.number(), new IncidentEvent(UUID.randomUUID(), incident.number(),
                    incident.shortDescription(), incident.orderId(), Instant.now(clock))).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException notSent) {
            LOGGER.warn("Could not announce incident {}; giving the claim back to try again", incident.number(), notSent);
            return false;
        }
    }

    private void handOverStaleClaims() {
        Instant staleBefore = Instant.now(clock).minus(properties.staleAfter());
        ServiceNowProperties.Team team = properties.teams().get(properties.defaultTeam());
        for (ServiceNowClient.Incident found : serviceNow.findClaimedByAgent()) {
            if (!isStaleClaim(found, staleBefore)) {
                continue;
            }
            ServiceNowClient.Incident incident = serviceNow.findByNumber(found.number()).orElse(null);
            if (incident == null || !isStaleClaim(incident, staleBefore)) {
                continue;
            }
            Map<String, String> handOver = new LinkedHashMap<>();
            handOver.put("assignment_group", team.group());
            handOver.put("assigned_to", "");
            handOver.put("work_notes", "The " + properties.agent() + " did not finish this incident within "
                    + properties.staleAfter().toMinutes() + " minutes, so it goes to " + team.group()
                    + ". Nothing in its notes is confirmed beyond what they say.");
            serviceNow.updateByDisplayValue(incident.sysId(), handOver);
            record("assign_to_team", "Handed " + incident.number() + " to " + team.group() + " because the agent did not finish it");
        }
    }

    /** Still the agent's, still in progress, and untouched since before {@code staleBefore}. */
    private boolean isStaleClaim(ServiceNowClient.Incident incident, Instant staleBefore) {
        return serviceNow.integrationUserSysId().equals(incident.assignedToSysId())
                && ServiceNowClient.STATE_IN_PROGRESS.equals(incident.state())
                && incident.updatedAt() != null && incident.updatedAt().isBefore(staleBefore);
    }

    /** Recorded in the shared audit trail as the agent the poller works for. */
    private void record(String tool, String summary) {
        try {
            governance.recordToolCall("Bearer " + agents.tokenOf(properties.agent()),
                    new GovernanceApi.ToolCall(null, "servicenow:" + tool, "SUCCEEDED", summary, null));
        } catch (RuntimeException unreachable) {
            LOGGER.warn("Could not record {} in the audit trail: {}", tool, summary, unreachable);
        }
    }
}
