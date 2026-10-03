package io.github.exepex.commerce.agent.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What agent-service reports when it cannot do what it was asked. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorMessages {

    public static final String UNKNOWN_AGENT = "Unknown agent %s";
    public static final String TOOL_CALLED_OUTSIDE_RUN = "Agent tools are only called within an agent run";
    public static final String KILL_SWITCH_UNREADABLE =
            "Could not read the kill switch, so incident %s was neither worked nor handed to a team";
    public static final String HAND_OFF_UNREACHABLE = "Could not hand incident %s to a team: %s";
    public static final String HAND_OFF_REJECTED = "ServiceNow did not take the hand-off of incident %s: %s";
}
