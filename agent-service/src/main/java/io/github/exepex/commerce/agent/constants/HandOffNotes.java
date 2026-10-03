package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The notes code leaves on a ServiceNow incident when it hands the incident to a team instead of the agent. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HandOffNotes {

    public static final String AGENT_SWITCHED_OFF = "The incident agent is switched off, so this incident goes straight "
            + "to a person. Nothing was checked or changed yet.";
    public static final String AGENT_FAILED =
            "The incident agent failed while working this incident (%s). Check its earlier work notes, then finish it.";
    public static final String NOT_FINISHED = "The incident agent finished without resolving this incident or handing "
            + "it to a team, so a person must finish it. The agent said: %s";
    /** Marks where a note too long for ServiceNow was cut. */
    public static final String ELLIPSIS = "…";
}
