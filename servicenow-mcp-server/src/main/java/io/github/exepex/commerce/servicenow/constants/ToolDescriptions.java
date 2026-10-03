package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the agent reads about each ServiceNow tool and its parameters. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolDescriptions {

    public static final String GET_INCIDENT = """
            Read an incident you are working: what was reported, by whom and when, its state and assignment, the \
            order it is linked to (empty when none), and its work notes and comments as ServiceNow shows them, newest \
            first. Each note starts with a line "<time> - <who> (<kind>)"; those times are in the ServiceNow user's \
            time zone, not UTC like openedAt.""";
    public static final String ADD_WORK_NOTE = """
            Add a work note to an incident you are working: what you checked, what you did and why. Work notes are \
            for the support teams, not the customer.""";
    public static final String LIST_TEAMS = "List the teams an incident can be handed to, and what each one handles.";
    public static final String ASSIGN_TO_TEAM = """
            Hand an incident you are working to the team whose work it is, when you cannot or should not finish it \
            yourself. The note must say what you found, what you already did, and what the team needs to decide or \
            do. The team is notified by ServiceNow; the incident is no longer yours afterwards.""";
    public static final String RESOLVE_INCIDENT = """
            Resolve an incident you are working, once it is fully handled. The resolution is shown to whoever reads \
            the incident: say what was wrong and what you did.""";

    public static final String INCIDENT_NUMBER_EXAMPLE = "The incident number, such as INC0010001";
    public static final String INCIDENT_NUMBER = "The incident number";
    public static final String NOTE = "The note";
    public static final String TEAM = "The team, as listed by list_teams; the default team when left out";
    public static final String HAND_OVER_NOTE = "What you found, what you did, and what the team needs to do";
    public static final String RESOLUTION = "What was wrong and what was done";
}
