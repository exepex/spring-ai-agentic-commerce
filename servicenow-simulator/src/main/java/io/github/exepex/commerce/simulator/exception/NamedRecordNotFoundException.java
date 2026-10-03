package io.github.exepex.commerce.simulator.exception;

import io.github.exepex.commerce.simulator.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** A reference field was given by a name, such as an assignment group's, that no row of its table has. */
public class NamedRecordNotFoundException extends SimulatorException {

    public NamedRecordNotFoundException(String table, String name) {
        super(HttpStatus.BAD_REQUEST, ErrorMessages.NAMED_RECORD_NOT_FOUND.formatted(table, name));
    }
}
