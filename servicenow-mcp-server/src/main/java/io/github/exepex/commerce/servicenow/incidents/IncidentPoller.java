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
 * Feeds ServiceNow incidents to the incident agent. Every poll first sends the shop's new cases to ServiceNow as
 * incidents in the agent's group (see {@link CaseSync}), then claims new incidents, then reads back who has each case's
 * incident. Each step runs even when another failed, so a governance API that is down does not stop incidents the
 * service desk raised from being worked.
 *
 * <p>Each new, unassigned incident in the agent's group is claimed by
 * assigning it to the integration user, then announced on Kafka: a claimed incident is no longer new, so it is never
 * announced twice. If Kafka does not take the announcement, the claim is given back, so the next poll claims and
 * announces the incident again. ServiceNow offers no outbound call without a public URL, so the demo asks every poll interval.
 *
 * <p>The Table API has no conditional update, so each incident is read again right before it is claimed, given back
 * or handed over, and left alone if a person took it or changed it meanwhile. That narrows the race with a person to
 * the time between that read and the update.
 *
 * <p>While the agent is switched off, new incidents in its group go straight to the default team instead of being
 * claimed, so they reach a person even when agent-service, which also checks the switch, is down.
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
    private final CaseSync cases;
    private final ServiceNowProperties properties;
    private final KafkaTemplate<String, IncidentEvent> kafka;
    private final String topic;
    private final GovernanceApi governance;
    private final AgentRegistry agents;
    private final Clock clock;

    IncidentPoller(ServiceNowClient serviceNow, CaseSync cases, ServiceNowProperties properties,
            KafkaTemplate<String, IncidentEvent> kafka, @Value("${commerce.topics.incidents}") String topic,
            GovernanceApi governance, AgentRegistry agents, Clock clock) {
        this.serviceNow = serviceNow;
        this.cases = cases;
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
        step("send the shop's cases to ServiceNow", cases::sendCases);
        step("hand over stale claims", this::handOverStaleClaims);
        step("claim new incidents", this::claimNewIncidents);
        step("read back the cases' incidents", cases::readBackIncidents);
    }

    private static void step(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Could not {}; trying again next poll", what, unavailable);
        }
    }

    /**
     * Claims each new incident in the agent's group, or, while the agent is switched off, hands it straight to the
     * default team: nothing would work a claimed incident then, even with agent-service down. While the switch cannot
     * be read, nothing is claimed or handed over; the incidents wait for the next poll.
     */
    private void claimNewIncidents() {
        boolean switchedOn = Boolean.TRUE.equals(governance.switches().get(properties.agent()));
        for (ServiceNowClient.Incident found : serviceNow.findNewForAgent()) {
            ServiceNowClient.Incident incident = serviceNow.findByNumber(found.number()).orElse(null);
            if (incident == null || incident.isAssigned() || !ServiceNowClient.STATE_NEW.equals(incident.state())
                    || !properties.agentGroup().equals(incident.assignmentGroup())) {
                continue;
            }
            if (!switchedOn) {
                handOverWhileSwitchedOff(incident);
                continue;
            }
            Map<String, String> claim = new LinkedHashMap<>();
            claim.put("assigned_to", serviceNow.integrationUserSysId());
            claim.put("state", ServiceNowClient.STATE_IN_PROGRESS);
            claim.put("work_notes", "Picked up by the " + properties.agent() + ".");
            serviceNow.update(incident.sysId(), claim);
            if (!announce(incident)) {
                giveBack(incident.number());
                continue;
            }
            record("claim_incident", "Claimed " + incident.number() + ": " + incident.shortDescription());
        }
    }

    private void handOverWhileSwitchedOff(ServiceNowClient.Incident incident) {
        ServiceNowProperties.Team team = properties.teams().get(properties.defaultTeam());
        Map<String, String> handOver = new LinkedHashMap<>();
        handOver.put("assignment_group", team.group());
        handOver.put("work_notes", "The " + properties.agent() + " is switched off, so this incident goes straight to "
                + team.group() + ". Nothing was checked or changed yet.");
        serviceNow.updateByDisplayValue(incident.sysId(), handOver);
        cases.reportHandedToTeam(incident, team.group());
        record("assign_to_team", "Handed " + incident.number() + " to " + team.group() + " because the agent is switched off");
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

    /**
     * Gives a claim back, so the next poll claims and announces the incident again: but only while it is still the
     * agent's claim. A person who took the incident, or moved it to another group, while Kafka was refusing the
     * announcement keeps it.
     */
    private void giveBack(String number) {
        ServiceNowClient.Incident incident = serviceNow.findByNumber(number).orElse(null);
        if (incident == null || !serviceNow.integrationUserSysId().equals(incident.assignedToSysId())
                || !ServiceNowClient.STATE_IN_PROGRESS.equals(incident.state())
                || !properties.agentGroup().equals(incident.assignmentGroup())) {
            LOGGER.info("Incident {} is no longer the agent's claim, so it is not given back", number);
            return;
        }
        Map<String, String> release = new LinkedHashMap<>();
        release.put("assigned_to", "");
        release.put("state", ServiceNowClient.STATE_NEW);
        serviceNow.update(incident.sysId(), release);
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
            cases.reportHandedToTeam(incident, team.group());
            record("assign_to_team", "Handed " + incident.number() + " to " + team.group() + " because the agent did not finish it");
        }
    }

    /**
     * Still the agent's, still in progress, still in the agent's group, and untouched since before {@code staleBefore}.
     * An incident a person moved to another group without changing its assignee stays where they put it.
     */
    private boolean isStaleClaim(ServiceNowClient.Incident incident, Instant staleBefore) {
        return serviceNow.integrationUserSysId().equals(incident.assignedToSysId())
                && ServiceNowClient.STATE_IN_PROGRESS.equals(incident.state())
                && properties.agentGroup().equals(incident.assignmentGroup())
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
