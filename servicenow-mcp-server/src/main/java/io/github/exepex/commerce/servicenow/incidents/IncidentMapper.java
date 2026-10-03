package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.dto.Team;
import io.github.exepex.commerce.servicenow.incidents.dto.Incident;
import io.github.exepex.commerce.servicenow.incidents.dto.IncidentView;
import io.github.exepex.commerce.servicenow.incidents.dto.Journal;
import io.github.exepex.commerce.servicenow.incidents.dto.TeamView;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How incidents and teams are shown to the agent through the ServiceNow tools. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class IncidentMapper {

    static IncidentView toView(Incident incident, Journal journal) {
        return new IncidentView(incident.number(), incident.shortDescription(), incident.description(),
                incident.stateName(), incident.caller(), incident.assignmentGroup(), incident.assignedTo(),
                incident.orderId(), incident.openedAt(), journal.workNotes(), journal.comments());
    }

    static List<TeamView> toViews(Map<String, Team> teams) {
        return teams.entrySet().stream()
                .map(team -> new TeamView(team.getKey(), team.getValue().handles()))
                .toList();
    }
}
