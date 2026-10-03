package io.github.exepex.commerce.servicenow.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What the server writes on incidents for the people who work them, and how it describes an incident to the shop.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IncidentTexts {

    public static final String CLAIMED = "Picked up by the %s.";
    public static final String HANDED_OVER_SWITCHED_OFF =
            "The %s is switched off, so this incident goes straight to %s. Nothing was checked or changed yet.";
    public static final String HANDED_OVER_UNFINISHED = "The %s did not finish this incident within %s minutes, so it "
            + "goes to %s. Nothing in its notes is confirmed beyond what they say.";

    /** Ends a case's note on its incident, so the incident shows which notes it already holds. */
    public static final String CASE_NOTE_MARKER = "[shop note %s]";
    public static final String CASE_NOTE_ENDING = "\n\n%s";

    /** The title of a service desk's incident recorded with the shop when it has no short description. */
    public static final String UNTITLED_INCIDENT = "Incident %s";
    public static final String NO_GROUP = "no group";
    /** A group and who in it has the incident, or why the agent cannot work it, such as On Hold. */
    public static final String GROUP_WITH_DETAIL = "%s (%s)";
}
