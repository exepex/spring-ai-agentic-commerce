package io.github.exepex.commerce.simulator.exception;

import io.github.exepex.commerce.simulator.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** The table asked for is not one the simulator keeps. */
public class InvalidTableException extends SimulatorException {

    public InvalidTableException(String table) {
        super(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_TABLE.formatted(table));
    }
}
