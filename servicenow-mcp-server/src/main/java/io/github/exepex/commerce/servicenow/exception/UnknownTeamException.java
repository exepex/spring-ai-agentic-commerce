package io.github.exepex.commerce.servicenow.exception;

import io.github.exepex.commerce.servicenow.constants.RefusalMessages;
import java.util.Set;

/** The agent named a team that is not configured. */
public class UnknownTeamException extends ToolRefusedException {

    public UnknownTeamException(String team, Set<String> teams) {
        super(RefusalMessages.UNKNOWN_TEAM.formatted(team, teams));
    }
}
