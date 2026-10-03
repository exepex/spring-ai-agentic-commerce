package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.constants.AuditValues;
import io.github.exepex.commerce.servicenow.constants.AuthValues;
import io.github.exepex.commerce.servicenow.constants.ConfigKeys;
import io.github.exepex.commerce.servicenow.constants.IncidentStates;
import io.github.exepex.commerce.servicenow.constants.IncidentTexts;
import io.github.exepex.commerce.servicenow.constants.ServiceNowFields;
import io.github.exepex.commerce.servicenow.constants.ToolNames;
import io.github.exepex.commerce.servicenow.dto.Team;
import io.github.exepex.commerce.servicenow.governance.GovernanceApi;
import io.github.exepex.commerce.servicenow.governance.LogValues;
import io.github.exepex.commerce.servicenow.governance.dto.ToolCall;
import io.github.exepex.commerce.servicenow.incidents.dto.Incident;
import io.github.exepex.commerce.servicenow.security.AgentRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Feeds ServiceNow incidents to the incident agent. Every poll first sends the shop's new cases to ServiceNow as
 * incidents in the agent's group (see {@link CaseSync}), records with the shop the incidents the service desk raised
 * about an order, then claims new incidents, then reads back who has each case's incident. Each step runs even when
 * another failed, so a governance API that is down does not stop incidents the service desk raised from being worked.
 * Only the read-back waits on the sending: it reports a case resolved only once its notes are settled, so never while
 * a note sent for it may have reached its incident unconfirmed, or while the cases to send could not be listed (see
 * {@link CaseSync#readBackIncidents}).
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
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentPoller {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final ServiceNowClient serviceNow;
    private final CaseSync cases;
    private final ServiceNowProperties properties;
    private final KafkaTemplate<String, IncidentEvent> kafka;

    @Value(ConfigKeys.INCIDENTS_TOPIC)
    private final String topic;

    private final GovernanceApi governance;
    private final AgentRegistry agents;
    private final Clock clock;

    @Scheduled(fixedDelayString = ConfigKeys.POLL_INTERVAL, initialDelayString = ConfigKeys.POLL_INTERVAL)
    public void poll() {
        if (!properties.isConfigured()) {
            return;
        }
        var unsent = new HashSet<UUID>();
        var listed = step("send the shop's cases to ServiceNow", () -> unsent.addAll(cases.sendCases()));
        step("hand over stale claims", this::handOverStaleClaims);
        var recorded = new HashMap<String, UUID>();
        step("record the service desk's incidents with the shop",
                () -> recorded.putAll(cases.recordServiceDeskIncidents()));
        step("claim new incidents", () -> claimNewIncidents(recorded));
        step("read back the cases' incidents",
                () -> cases.readBackIncidents(caseId -> listed && !unsent.contains(caseId)));
    }

    /** Whether the step ran to the end. */
    private static boolean step(String what, Runnable step) {
        try {
            step.run();
            return true;
        } catch (RuntimeException unavailable) {
            log.warn("Could not {}; trying again next poll", what, unavailable);
            return false;
        }
    }

    /**
     * Claims each new incident in the agent's group, or, while the agent is switched off, hands it straight to the
     * default team: nothing would work a claimed incident then, even with agent-service down. While the switch cannot
     * be read, nothing is claimed or handed over; the incidents wait for the next poll.
     *
     * <p>An incident the service desk raised about an order is claimed only once the shop has it as a case, so that
     * agents leave the order's money to whoever works it from the start. One the shop could not be told about this poll
     * waits for the next: claimed and resolved before then, it would never be recorded, since only open incidents are.
     * Handing it to the default team while the agent is switched off does not wait: it stays open with a person, and
     * the shop records it once it can be told.
     *
     * <p>The shop must have it under the order the incident names when it is claimed: one the service desk moved to
     * another order since it was recorded waits for the next poll, which moves the case first.
     *
     * @param recorded the service desk's incidents the shop has now, by number, each with its order
     */
    private void claimNewIncidents(Map<String, UUID> recorded) {
        var switchedOn = Boolean.TRUE.equals(governance.switches().get(properties.agent()));
        for (var found : serviceNow.findNewForAgent()) {
            serviceNow.findByNumber(found.number())
                    .filter(this::isNewInTheAgentsGroup)
                    .ifPresent(incident -> claim(incident, switchedOn, recorded));
        }
    }

    /** Read again right before claiming: a person may have taken or moved the incident since it was listed. */
    private boolean isNewInTheAgentsGroup(Incident incident) {
        return !incident.isAssigned() && IncidentStates.NEW.equals(incident.state())
                && properties.agentGroup().equals(incident.assignmentGroup());
    }

    /** Claims the incident for the agent and announces it; a switched-off agent's incident goes to a team instead. */
    private void claim(Incident incident, boolean switchedOn, Map<String, UUID> recorded) {
        if (!switchedOn) {
            handOverWhileSwitchedOff(incident);
            return;
        }
        if (cases.isServiceDeskIncidentAboutAnOrder(incident) && !CaseSync.isRecordedForItsOrder(incident, recorded)) {
            log.info("{} waits until the shop has it as a case", incident.number());
            return;
        }
        var claim = new LinkedHashMap<String, String>();
        claim.put(ServiceNowFields.ASSIGNED_TO, serviceNow.integrationUserSysId());
        claim.put(ServiceNowFields.STATE, IncidentStates.IN_PROGRESS);
        claim.put(ServiceNowFields.WORK_NOTES, IncidentTexts.CLAIMED.formatted(properties.agent()));
        serviceNow.update(incident.sysId(), claim);
        if (!announce(incident)) {
            giveBack(incident.number());
            return;
        }
        record(ToolNames.CLAIM_INCIDENT, AuditValues.CLAIMED.formatted(incident.number(), incident.shortDescription()));
    }

    private void handOverWhileSwitchedOff(Incident incident) {
        var team = properties.teams().get(properties.defaultTeam());
        var handOver = new LinkedHashMap<String, String>();
        handOver.put(ServiceNowFields.ASSIGNMENT_GROUP, team.group());
        handOver.put(ServiceNowFields.WORK_NOTES,
                IncidentTexts.HANDED_OVER_SWITCHED_OFF.formatted(properties.agent(), team.group()));
        serviceNow.updateByDisplayValue(incident.sysId(), handOver);
        cases.reportHandedToTeam(incident, team.group());
        record(ToolNames.ASSIGN_TO_TEAM,
                AuditValues.HANDED_OVER_SWITCHED_OFF.formatted(incident.number(), team.group()));
    }

    /** Whether Kafka took the announcement. */
    private boolean announce(Incident incident) {
        try {
            kafka.send(topic, incident.number(), new IncidentEvent(UUID.randomUUID(), incident.number(),
                    incident.shortDescription(), incident.orderId(), Instant.now(clock))).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException notSent) {
            log.warn("Could not announce incident {}; giving the claim back to try again", incident.number(), notSent);
            return false;
        }
    }

    /**
     * Gives a claim back, so the next poll claims and announces the incident again: but only while it is still the
     * agent's claim. A person who took the incident, or moved it to another group, while Kafka was refusing the
     * announcement keeps it.
     */
    private void giveBack(String number) {
        var incident = serviceNow.findByNumber(number).orElse(null);
        if (incident == null || !incident.isClaimedBy(serviceNow.integrationUserSysId(), properties.agentGroup())) {
            log.info("Incident {} is no longer the agent's claim, so it is not given back", number);
            return;
        }
        var release = new LinkedHashMap<String, String>();
        release.put(ServiceNowFields.ASSIGNED_TO, "");
        release.put(ServiceNowFields.STATE, IncidentStates.NEW);
        serviceNow.update(incident.sysId(), release);
    }

    private void handOverStaleClaims() {
        var staleBefore = Instant.now(clock).minus(properties.staleAfter());
        var team = properties.teams().get(properties.defaultTeam());
        for (var found : serviceNow.findClaimedByAgent()) {
            if (isStaleClaim(found, staleBefore)) {
                serviceNow.findByNumber(found.number())
                        .filter(incident -> isStaleClaim(incident, staleBefore))
                        .ifPresent(incident -> handOverStaleClaim(incident, team));
            }
        }
    }

    /** Hands a claim the agent did not finish in time to the default team. */
    private void handOverStaleClaim(Incident incident, Team team) {
        var handOver = new LinkedHashMap<String, String>();
        handOver.put(ServiceNowFields.ASSIGNMENT_GROUP, team.group());
        handOver.put(ServiceNowFields.ASSIGNED_TO, "");
        handOver.put(ServiceNowFields.WORK_NOTES, IncidentTexts.HANDED_OVER_UNFINISHED.formatted(properties.agent(),
                properties.staleAfter().toMinutes(), team.group()));
        serviceNow.updateByDisplayValue(incident.sysId(), handOver);
        cases.reportHandedToTeam(incident, team.group());
        record(ToolNames.ASSIGN_TO_TEAM, AuditValues.HANDED_OVER_UNFINISHED.formatted(incident.number(), team.group()));
    }

    /**
     * Still the agent's, still in progress, still in the agent's group, and untouched since before {@code staleBefore}.
     * An incident a person moved to another group without changing its assignee stays where they put it.
     */
    private boolean isStaleClaim(Incident incident, Instant staleBefore) {
        return incident.isClaimedBy(serviceNow.integrationUserSysId(), properties.agentGroup())
                && incident.updatedAt() != null && incident.updatedAt().isBefore(staleBefore);
    }

    /** Recorded in the shared audit trail as the agent the poller works for. */
    private void record(String tool, String summary) {
        try {
            governance.recordToolCall(AuthValues.BEARER_PREFIX + agents.tokenOf(properties.agent()),
                    new ToolCall(null, AuditValues.ACTION_PREFIX + tool, AuditValues.SUCCEEDED, summary, null));
        } catch (RuntimeException unreachable) {
            log.warn("Could not record {} in the audit trail: {}", tool, LogValues.safe(summary), unreachable);
        }
    }
}
