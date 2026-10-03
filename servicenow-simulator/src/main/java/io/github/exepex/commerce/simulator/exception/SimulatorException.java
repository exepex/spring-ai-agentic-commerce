package io.github.exepex.commerce.simulator.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** A Table API request the simulator cannot carry out; the status says how a caller should take it. */
@Getter
public abstract class SimulatorException extends RuntimeException {

    private final HttpStatus status;

    protected SimulatorException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
