package io.github.exepex.commerce.simulator.exception;

import io.github.exepex.commerce.simulator.constants.ErrorMessages;
import org.springframework.http.HttpStatus;

/** A term of the encoded query is not one the simulator understands. */
public class InvalidEncodedQueryException extends SimulatorException {

    public InvalidEncodedQueryException(String term) {
        super(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_QUERY_TERM.formatted(term));
    }
}
