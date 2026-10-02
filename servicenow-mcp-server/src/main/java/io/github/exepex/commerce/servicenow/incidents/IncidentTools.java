package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.governance.ToolGuard;
import io.github.exepex.commerce.servicenow.governance.ToolRefusedException;
import io.modelcontextprotocol.common.McpTransportContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The ServiceNow tools. An agent may only work an incident it owns, one assigned to the integration user: it cannot
 * touch incidents that belong to a person or another team, and once it hands an incident to a team it is no longer
 * its to change.
 */
@Component
class IncidentTools {

    record Note(Instant at, String by, String kind, String text) {}

    record IncidentView(String number, String shortDescription, String description, String state, String caller,
            String assignmentGroup, String assignedTo, List<Note> notes) {}

    record TeamView(String team, String handles) {}

    record Acknowledgement(String number, String message) {}

    private static final Pattern INCIDENT_NUMBER = Pattern.compile("INC\\d{7,}");
    private static final int MAX_NOTE_LENGTH = 4000;

    private final ToolGuard guard;
    private final ServiceNowClient serviceNow;
    private final ServiceNowProperties properties;

    IncidentTools(ToolGuard guard, ServiceNowClient serviceNow, ServiceNowProperties properties) {
        this.guard = guard;
        this.serviceNow = serviceNow;
        this.properties = properties;
    }

    @McpTool(name = "get_incident", description = """
            Read an incident: what was reported, by whom, its state and assignment, and its work notes and comments, \
            oldest first.""")
    IncidentView getIncident(McpTransportContext context,
            @McpToolParam(description = "The incident number, such as INC0010001") String number) {
        return guard.run(context, "get_incident", "Read incident " + number, agentId -> {
            ServiceNowClient.Incident incident = find(number);
            List<Note> notes = serviceNow.journalOf(incident.sysId()).stream()
                    .map(entry -> new Note(entry.at(), entry.by(), entry.kind(), entry.text()))
                    .toList();
            return new IncidentView(incident.number(), incident.shortDescription(), incident.description(),
                    incident.stateName(), incident.caller(), incident.assignmentGroup(), incident.assignedTo(), notes);
        });
    }

    @McpTool(name = "add_work_note", description = """
            Add a work note to an incident you are working: what you checked, what you did and why. Work notes are \
            for the support teams, not the customer.""")
    Acknowledgement addWorkNote(McpTransportContext context,
            @McpToolParam(description = "The incident number") String number,
            @McpToolParam(description = "The note") String note) {
        return guard.run(context, "add_work_note", "Added a work note to " + number, agentId -> {
            ServiceNowClient.Incident incident = owned(number);
            serviceNow.update(incident.sysId(), Map.of("work_notes", requireNote(note)));
            return new Acknowledgement(number, "The work note was added");
        });
    }

    @McpTool(name = "list_teams", description = "List the teams an incident can be handed to, and what each one handles.")
    List<TeamView> listTeams(McpTransportContext context) {
        return guard.run(context, "list_teams", "Listed the teams", agentId -> properties.teams().entrySet().stream()
                .map(team -> new TeamView(team.getKey(), team.getValue().handles()))
                .toList());
    }

    @McpTool(name = ToolGuard.HAND_TO_TEAM, description = """
            Hand an incident you are working to the team whose work it is, when you cannot or should not finish it \
            yourself. The note must say what you found, what you already did, and what the team needs to decide or \
            do. The team is notified by ServiceNow; the incident is no longer yours afterwards.""")
    Acknowledgement assignToTeam(McpTransportContext context,
            @McpToolParam(description = "The incident number") String number,
            @McpToolParam(description = "The team, as listed by list_teams") String team,
            @McpToolParam(description = "What you found, what you did, and what the team needs to do") String note) {
        return guard.run(context, ToolGuard.HAND_TO_TEAM, "Assigned " + number + " to team " + team, agentId -> {
            String teamKey = team == null || team.isBlank() ? properties.defaultTeam() : team;
            ServiceNowProperties.Team target = properties.teams().get(teamKey);
            if (target == null) {
                throw new ToolRefusedException("There is no team '" + team + "'. Use one of " + properties.teams().keySet() + ".");
            }
            ServiceNowClient.Incident incident = owned(number);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("assignment_group", target.group());
            fields.put("assigned_to", "");
            fields.put("work_notes", requireNote(note));
            serviceNow.updateByDisplayValue(incident.sysId(), fields);
            return new Acknowledgement(number, "Assigned to " + target.group() + ", who are notified by ServiceNow");
        });
    }

    @McpTool(name = "resolve_incident", description = """
            Resolve an incident you are working, once it is fully handled. The resolution is shown to whoever reads \
            the incident: say what was wrong and what you did.""")
    Acknowledgement resolveIncident(McpTransportContext context,
            @McpToolParam(description = "The incident number") String number,
            @McpToolParam(description = "What was wrong and what was done") String resolution) {
        return guard.run(context, "resolve_incident", "Resolved " + number, agentId -> {
            ServiceNowClient.Incident incident = owned(number);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("state", ServiceNowClient.STATE_RESOLVED);
            fields.put("close_code", properties.closeCode());
            fields.put("close_notes", requireNote(resolution));
            serviceNow.update(incident.sysId(), fields);
            return new Acknowledgement(number, "The incident is resolved");
        });
    }

    private ServiceNowClient.Incident find(String number) {
        if (!properties.isConfigured()) {
            throw new ToolRefusedException("ServiceNow is not configured");
        }
        if (number == null || !INCIDENT_NUMBER.matcher(number).matches()) {
            throw new ToolRefusedException("'" + number + "' is not an incident number such as INC0010001");
        }
        return serviceNow.findByNumber(number)
                .orElseThrow(() -> new ToolRefusedException("Incident " + number + " does not exist"));
    }

    /** The incident, if the agent is the one working it; otherwise the call is refused. */
    private ServiceNowClient.Incident owned(String number) {
        ServiceNowClient.Incident incident = find(number);
        if (!serviceNow.integrationUserSysId().equals(incident.assignedToSysId())
                || !ServiceNowClient.STATE_IN_PROGRESS.equals(incident.state())) {
            throw new ToolRefusedException("Incident " + number + " is not yours to change: it is " + incident.stateName()
                    + (incident.isAssigned() ? " and assigned to " + incident.assignedTo() : " and unassigned")
                    + " in " + incident.assignmentGroup() + ".");
        }
        return incident;
    }

    private static String requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new ToolRefusedException("The note cannot be empty");
        }
        if (note.length() > MAX_NOTE_LENGTH) {
            throw new ToolRefusedException("A note can be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return note;
    }
}
