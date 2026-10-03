package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the ServiceNow tools answer the agent with, and how incidents and teams turn into it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class IncidentViews {

    record IncidentView(String number, String shortDescription, String description, String state, String caller,
            String assignmentGroup, String assignedTo, String linkedOrderId, Instant openedAt, String workNotes,
            String comments) {}

    record TeamView(String team, String handles) {}

    record Acknowledgement(String number, String message) {}

    static IncidentView toView(ServiceNowClient.Incident incident, ServiceNowClient.Journal journal) {
        return new IncidentView(incident.number(), incident.shortDescription(), incident.description(),
                incident.stateName(), incident.caller(), incident.assignmentGroup(), incident.assignedTo(),
                incident.orderId(), incident.openedAt(), journal.workNotes(), journal.comments());
    }

    static List<TeamView> toViews(Map<String, ServiceNowProperties.Team> teams) {
        return teams.entrySet().stream()
                .map(team -> new TeamView(team.getKey(), team.getValue().handles()))
                .toList();
    }
}
