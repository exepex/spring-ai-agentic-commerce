package io.github.exepex.commerce.servicenow.incidents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.governance.GovernanceApi;
import io.github.exepex.commerce.servicenow.security.AgentRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class IncidentPollerTest {

    private static final ServiceNowProperties PROPERTIES = new ServiceNowProperties("https://dev.example.com",
            "agent.user", "secret", "incident-agent", "Online Shop Agent", Duration.ofMinutes(15), "Solution provided",
            "customer-care", Map.of("customer-care", new ServiceNowProperties.Team("Customer Care", "everything else")));
    private static final ServiceNowClient.Incident NEW_INCIDENT = new ServiceNowClient.Incident("sys-1", "INC0010001",
            "Order arrived broken", "", ServiceNowClient.STATE_NEW, "New", "Online Shop Agent", "", "", "Ada", "", "", null, null);

    private final ServiceNowClient serviceNow = mock(ServiceNowClient.class);
    private final GovernanceApi governance = mock(GovernanceApi.class);

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, IncidentEvent> kafka = mock(KafkaTemplate.class);

    @Test
    void aClaimWhoseAnnouncementKafkaDidNotTakeIsGivenBack() {
        when(serviceNow.integrationUserSysId()).thenReturn("agent-sys-id");
        when(serviceNow.findClaimedByAgent()).thenReturn(List.of());
        when(serviceNow.findNewForAgent()).thenReturn(List.of(NEW_INCIDENT));
        ServiceNowClient.Incident claimed = new ServiceNowClient.Incident("sys-1", "INC0010001", "Order arrived broken", "",
                ServiceNowClient.STATE_IN_PROGRESS, "In Progress", "Online Shop Agent", "agent-sys-id", "Agent", "Ada", "",
                "", null, null);
        when(serviceNow.findByNumber("INC0010001")).thenReturn(Optional.of(NEW_INCIDENT), Optional.of(claimed));
        when(kafka.send(eq("servicenow.incidents"), eq("INC0010001"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka is down")));

        new IncidentPoller(serviceNow, mock(CaseSync.class), PROPERTIES, kafka, "servicenow.incidents", governance, mock(AgentRegistry.class),
                Clock.systemUTC()).poll();

        verify(serviceNow).update("sys-1", Map.of("assigned_to", "agent-sys-id", "state", ServiceNowClient.STATE_IN_PROGRESS,
                "work_notes", "Picked up by the incident-agent."));
        verify(serviceNow).update("sys-1", Map.of("assigned_to", "", "state", ServiceNowClient.STATE_NEW));
        verify(governance, never()).recordToolCall(any(), any());
    }

    @Test
    void anIncidentAPersonTookWhileKafkaRefusedTheAnnouncementStaysTheirs() {
        ServiceNowClient.Incident takenByAPerson = new ServiceNowClient.Incident("sys-1", "INC0010001",
                "Order arrived broken", "", ServiceNowClient.STATE_IN_PROGRESS, "In Progress", "Online Shop Agent",
                "desk-ana", "Ana", "Ada", "", "", null, null);
        when(serviceNow.integrationUserSysId()).thenReturn("agent-sys-id");
        when(serviceNow.findClaimedByAgent()).thenReturn(List.of());
        when(serviceNow.findNewForAgent()).thenReturn(List.of(NEW_INCIDENT));
        when(serviceNow.findByNumber("INC0010001")).thenReturn(Optional.of(NEW_INCIDENT), Optional.of(takenByAPerson));
        when(kafka.send(eq("servicenow.incidents"), eq("INC0010001"), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka is down")));

        new IncidentPoller(serviceNow, mock(CaseSync.class), PROPERTIES, kafka, "servicenow.incidents", governance,
                mock(AgentRegistry.class), Clock.systemUTC()).poll();

        verify(serviceNow, never()).update("sys-1", Map.of("assigned_to", "", "state", ServiceNowClient.STATE_NEW));
    }
}
