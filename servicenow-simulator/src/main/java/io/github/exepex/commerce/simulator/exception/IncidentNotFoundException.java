package io.github.exepex.commerce.simulator.exception;

import io.github.exepex.commerce.simulator.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** No incident has the sys_id an update names. */
public class IncidentNotFoundException extends SimulatorException {

    public IncidentNotFoundException(String sysId) {
        super(HttpStatus.NOT_FOUND, ErrorMessages.INCIDENT_NOT_FOUND.formatted(sysId));
    }
}
