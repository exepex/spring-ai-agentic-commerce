package io.github.exepex.commerce.servicenow.incidents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.constants.IncidentStates;
import io.github.exepex.commerce.servicenow.dto.Team;
import io.github.exepex.commerce.servicenow.governance.GovernanceApi;
import io.github.exepex.commerce.servicenow.incidents.dto.Incident;
import io.github.exepex.commerce.servicenow.security.AgentRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class IncidentPollerTest {

    private static final ServiceNowProperties PROPERTIES = new ServiceNowProperties("https://dev.example.com",
            "agent.user", "secret", "incident-agent", "Online Shop Agent", Duration.ofMinutes(15), "Solution provided",
            "customer-care", Map.of("customer-care", new Team("Customer Care", "everything else")));
    private static final Incident NEW_INCIDENT = new Incident("sys-1", "INC0010001",
            "Order arrived broken", "", IncidentStates.NEW, "New", "Online Shop Agent", "", "", "Ada", "", "", null, null);
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private final ServiceNowClient serviceNow = mock(ServiceNowClient.class);
    private final GovernanceApi governance = mock(GovernanceApi.class);

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, IncidentEvent> kafka = mock(KafkaTemplate.class);

    @BeforeEach
    void switchTheAgentOn() {
        when(governance.switches()).thenReturn(Map.of("incident-agent", true));
    }

    @Test
    void aClaimWhoseAnnouncementKafkaDidNotTakeIsGivenBack() {
        when(serviceNow.integrationUserSysId()).thenReturn("agent-sys-id");
        when(serviceNow.findClaimedByAgent()).thenReturn(List.of());
        when(serviceNow.findNewForAgent()).thenReturn(List.of(NEW_INCIDENT));
        Incident claimed = new Incident("sys-1", "INC0010001", "Order arrived broken", "",
                IncidentStates.IN_PROGRESS, "In Progress", "Online Shop Agent", "agent-sys-id", "Agent", "Ada", "",
                "", null, null);
        when(serviceNow.findByNumber("INC0010001")).thenReturn(Optional.of(NEW_INCIDENT), Optional.of(claimed));
        when(kafka.send(eq("servicenow.incidents"), eq("INC0010001"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka is down")));

        new IncidentPoller(serviceNow, mock(CaseSync.class), PROPERTIES, kafka, "servicenow.incidents", governance, mock(AgentRegistry.class),
                Clock.systemUTC()).poll();

        verify(serviceNow).update("sys-1", Map.of("assigned_to", "agent-sys-id", "state", IncidentStates.IN_PROGRESS,
                "work_notes", "Picked up by the incident-agent."));
        verify(serviceNow).update("sys-1", Map.of("assigned_to", "", "state", IncidentStates.NEW));
        verify(governance, never()).recordToolCall(any(), any());
    }

    @Test
    void anIncidentAPersonTookWhileKafkaRefusedTheAnnouncementStaysTheirs() {
        Incident takenByAPerson = new Incident("sys-1", "INC0010001",
                "Order arrived broken", "", IncidentStates.IN_PROGRESS, "In Progress", "Online Shop Agent",
                "desk-ana", "Ana", "Ada", "", "", null, null);
        when(serviceNow.integrationUserSysId()).thenReturn("agent-sys-id");
        when(serviceNow.findClaimedByAgent()).thenReturn(List.of());
        when(serviceNow.findNewForAgent()).thenReturn(List.of(NEW_INCIDENT));
        when(serviceNow.findByNumber("INC0010001")).thenReturn(Optional.of(NEW_INCIDENT), Optional.of(takenByAPerson));
        when(kafka.send(eq("servicenow.incidents"), eq("INC0010001"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka is down")));

        new IncidentPoller(serviceNow, mock(CaseSync.class), PROPERTIES, kafka, "servicenow.incidents", governance,
                mock(AgentRegistry.class), Clock.systemUTC()).poll();

        verify(serviceNow, never()).update("sys-1", Map.of("assigned_to", "", "state", IncidentStates.NEW));
    }

    @Test
    void anIncidentMovedToAnotherGroupWhileKafkaRefusedTheAnnouncementStaysThere() {
        Incident takenByAPerson = new Incident("sys-1", "INC0010001",
                "Order arrived broken", "", IncidentStates.IN_PROGRESS, "In Progress", "Payments",
                "agent-sys-id", "Agent", "Ada", "", "", null, null);
        when(serviceNow.integrationUserSysId()).thenReturn("agent-sys-id");
        when(serviceNow.findClaimedByAgent()).thenReturn(List.of());
        when(serviceNow.findNewForAgent()).thenReturn(List.of(NEW_INCIDENT));
        when(serviceNow.findByNumber("INC0010001")).thenReturn(Optional.of(NEW_INCIDENT), Optional.of(takenByAPerson));
        when(kafka.send(eq("servicenow.incidents"), eq("INC0010001"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka is down")));

        new IncidentPoller(serviceNow, mock(CaseSync.class), PROPERTIES, kafka, "servicenow.incidents", governance,
                mock(AgentRegistry.class), Clock.systemUTC()).poll();

        verify(serviceNow, never()).update("sys-1", Map.of("assigned_to", "", "state", IncidentStates.NEW));
    }

    @Test
    void aStaleClaimStillInTheAgentGroupGoesToTheDefaultTeam() {
        Incident stale = claimedLongAgoIn("Online Shop Agent");
        when(serviceNow.integrationUserSysId()).thenReturn("agent-sys-id");
        when(serviceNow.findClaimedByAgent()).thenReturn(List.of(stale));
        when(serviceNow.findByNumber("INC0010001")).thenReturn(Optional.of(stale));
        when(serviceNow.findNewForAgent()).thenReturn(List.of());

        pollAt(NOW);

        verify(serviceNow).updateByDisplayValue(eq("sys-1"), anyMap());
    }

    @Test
    void aStaleClaimAPersonMovedToAnotherGroupStaysThere() {
        Incident moved = claimedLongAgoIn("Payments");
        when(serviceNow.integrationUserSysId()).thenReturn("agent-sys-id");
        when(serviceNow.findClaimedByAgent()).thenReturn(List.of(moved));
        when(serviceNow.findByNumber("INC0010001")).thenReturn(Optional.of(moved));
        when(serviceNow.findNewForAgent()).thenReturn(List.of());

        pollAt(NOW);

        verify(serviceNow, never()).updateByDisplayValue(any(), anyMap());
    }

    /** In progress and assigned to the integration user, untouched for an hour, in the given group. */
    private static Incident claimedLongAgoIn(String group) {
        return new Incident("sys-1", "INC0010001", "Order arrived broken", "",
                IncidentStates.IN_PROGRESS, "In Progress", group, "agent-sys-id", "Agent", "Ada", "", "", null,
                NOW.minus(Duration.ofHours(1)));
    }

    private void pollAt(Instant now) {
        new IncidentPoller(serviceNow, mock(CaseSync.class), PROPERTIES, kafka, "servicenow.incidents", governance,
                mock(AgentRegistry.class), Clock.fixed(now, ZoneOffset.UTC)).poll();
    }
}
