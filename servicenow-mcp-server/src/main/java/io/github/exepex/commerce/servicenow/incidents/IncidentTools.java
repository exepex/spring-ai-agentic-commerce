package io.github.exepex.commerce.servicenow.incidents;

import io.github.exepex.commerce.servicenow.ServiceNowProperties;
import io.github.exepex.commerce.servicenow.constants.AuditValues;
import io.github.exepex.commerce.servicenow.constants.IncidentStates;
import io.github.exepex.commerce.servicenow.constants.Patterns;
import io.github.exepex.commerce.servicenow.constants.ServiceNowFields;
import io.github.exepex.commerce.servicenow.constants.ToolDescriptions;
import io.github.exepex.commerce.servicenow.constants.ToolNames;
import io.github.exepex.commerce.servicenow.constants.ToolResults;
import io.github.exepex.commerce.servicenow.exception.EmptyNoteException;
import io.github.exepex.commerce.servicenow.exception.IncidentNotFoundException;
import io.github.exepex.commerce.servicenow.exception.IncidentNotOwnedException;
import io.github.exepex.commerce.servicenow.exception.InvalidIncidentNumberException;
import io.github.exepex.commerce.servicenow.exception.NoteTooLongException;
import io.github.exepex.commerce.servicenow.exception.ServiceNowNotConfiguredException;
import io.github.exepex.commerce.servicenow.exception.UnknownTeamException;
import io.github.exepex.commerce.servicenow.governance.ToolGuard;
import io.github.exepex.commerce.servicenow.incidents.dto.Acknowledgement;
import io.github.exepex.commerce.servicenow.incidents.dto.Incident;
import io.github.exepex.commerce.servicenow.incidents.dto.IncidentView;
import io.github.exepex.commerce.servicenow.incidents.dto.TeamView;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The ServiceNow tools. An agent may only work an incident it owns, one assigned to the integration user: it cannot
 * touch incidents that belong to a person or another team, and once it hands an incident to a team it is no longer
 * its to change.
 */
@Component
@RequiredArgsConstructor
class IncidentTools {

    private static final Pattern INCIDENT_NUMBER = Pattern.compile(Patterns.INCIDENT_NUMBER);
    private static final int MAX_NOTE_LENGTH = 4000;

    private final ToolGuard guard;
    private final IncidentSystem serviceNow;
    private final CaseSync cases;
    private final ServiceNowProperties properties;

    @McpTool(name = ToolNames.GET_INCIDENT, description = ToolDescriptions.GET_INCIDENT)
    IncidentView getIncident(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.INCIDENT_NUMBER_EXAMPLE) String number) {
        return guard.run(context, ToolNames.GET_INCIDENT, AuditValues.READ_INCIDENT.formatted(number), agentId -> {
            var incident = owned(number);
            return IncidentMapper.toView(incident, serviceNow.journalOf(incident.sysId()));
        });
    }

    @McpTool(name = ToolNames.ADD_WORK_NOTE, description = ToolDescriptions.ADD_WORK_NOTE)
    Acknowledgement addWorkNote(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.INCIDENT_NUMBER) String number,
            @McpToolParam(description = ToolDescriptions.NOTE) String note) {
        return guard.run(context, ToolNames.ADD_WORK_NOTE, AuditValues.ADDED_WORK_NOTE.formatted(number), agentId -> {
            var incident = owned(number);
            serviceNow.update(incident.sysId(), Map.of(ServiceNowFields.WORK_NOTES, requireNote(note)));
            return new Acknowledgement(number, ToolResults.WORK_NOTE_ADDED);
        });
    }

    @McpTool(name = ToolNames.LIST_TEAMS, description = ToolDescriptions.LIST_TEAMS)
    List<TeamView> listTeams(McpTransportContext context) {
        return guard.run(context, ToolNames.LIST_TEAMS, AuditValues.LISTED_TEAMS,
                agentId -> IncidentMapper.toViews(properties.teams()));
    }

    @McpTool(name = ToolNames.ASSIGN_TO_TEAM, description = ToolDescriptions.ASSIGN_TO_TEAM)
    Acknowledgement assignToTeam(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.INCIDENT_NUMBER) String number,
            @McpToolParam(description = ToolDescriptions.TEAM, required = false) String team,
            @McpToolParam(description = ToolDescriptions.HAND_OVER_NOTE) String note) {
        return guard.run(context, ToolNames.ASSIGN_TO_TEAM, AuditValues.ASSIGNED_TO_TEAM.formatted(number, team),
                agentId -> {
                    var teamKey = team == null || team.isBlank() ? properties.defaultTeam() : team;
                    var target = properties.teams().get(teamKey);
                    if (target == null) {
                        throw new UnknownTeamException(team, properties.teams().keySet());
                    }
                    var incident = owned(number);
                    var fields = new LinkedHashMap<String, String>();
                    fields.put(ServiceNowFields.ASSIGNMENT_GROUP, target.group());
                    fields.put(ServiceNowFields.ASSIGNED_TO, "");
                    fields.put(ServiceNowFields.WORK_NOTES, requireNote(note));
                    serviceNow.updateByDisplayValue(incident.sysId(), fields);
                    cases.reportHandedToTeam(incident, target.group());
                    return new Acknowledgement(number, ToolResults.ASSIGNED_TO_TEAM.formatted(target.group()));
                });
    }

    @McpTool(name = ToolNames.RESOLVE_INCIDENT, description = ToolDescriptions.RESOLVE_INCIDENT)
    Acknowledgement resolveIncident(McpTransportContext context,
            @McpToolParam(description = ToolDescriptions.INCIDENT_NUMBER) String number,
            @McpToolParam(description = ToolDescriptions.RESOLUTION) String resolution) {
        return guard.run(context, ToolNames.RESOLVE_INCIDENT, AuditValues.RESOLVED.formatted(number), agentId -> {
            var incident = owned(number);
            var fields = new LinkedHashMap<String, String>();
            fields.put(ServiceNowFields.STATE, IncidentStates.RESOLVED);
            fields.put(ServiceNowFields.CLOSE_CODE, properties.closeCode());
            fields.put(ServiceNowFields.CLOSE_NOTES, requireNote(resolution));
            serviceNow.update(incident.sysId(), fields);
            return new Acknowledgement(number, ToolResults.INCIDENT_RESOLVED);
        });
    }

    private Incident find(String number) {
        if (!properties.isConfigured()) {
            throw new ServiceNowNotConfiguredException();
        }
        if (number == null || !INCIDENT_NUMBER.matcher(number).matches()) {
            throw new InvalidIncidentNumberException(number);
        }
        return serviceNow.findByNumber(number).orElseThrow(() -> new IncidentNotFoundException(number));
    }

    /** The incident, if the agent is the one working it; otherwise the call is refused. */
    private Incident owned(String number) {
        var incident = find(number);
        if (!incident.isClaimedBy(serviceNow.integrationUserSysId(), properties.agentGroup())) {
            throw new IncidentNotOwnedException(incident);
        }
        return incident;
    }

    private static String requireNote(String note) {
        if (note == null || note.isBlank()) {
            throw new EmptyNoteException();
        }
        if (note.length() > MAX_NOTE_LENGTH) {
            throw new NoteTooLongException(MAX_NOTE_LENGTH);
        }
        return note;
    }
}
