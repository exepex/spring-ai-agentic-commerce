package io.github.exepex.commerce.agent.exception;

import io.github.exepex.commerce.agent.constants.ErrorMessages;

/**
 * The incident agent's kill switch could not be read, so the incident was neither worked nor handed over: Kafka keeps
 * delivering it until the agent or a team gets it.
 */
public class KillSwitchUnreadableException extends HandOffFailedException {

    public KillSwitchUnreadableException(String incidentNumber, Throwable cause) {
        super(ErrorMessages.KILL_SWITCH_UNREADABLE.formatted(incidentNumber), cause);
    }
}
