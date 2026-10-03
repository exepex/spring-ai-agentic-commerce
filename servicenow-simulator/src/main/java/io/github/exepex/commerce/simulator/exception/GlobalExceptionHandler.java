package io.github.exepex.commerce.simulator.exception;

import io.github.exepex.commerce.simulator.ServiceNowSimulatorApplication;
import io.github.exepex.commerce.simulator.constants.TableApi;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers the Table API's failed requests. A query or a value the simulator cannot take is answered as a real instance
 * answers one: a failure with its message. So is a parameter Spring cannot read, such as a {@code sysparm_limit} that
 * is no number. An unknown table or incident is answered with Spring Boot's error page for its status.
 */
@RestControllerAdvice(basePackageClasses = ServiceNowSimulatorApplication.class)
public class GlobalExceptionHandler {

    @ExceptionHandler({InvalidEncodedQueryException.class, NamedRecordNotFoundException.class})
    ResponseEntity<Map<String, Object>> handleRefusedRequest(SimulatorException refused) {
        return failure(refused.getStatus(), refused.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> handleInvalidArgument(IllegalArgumentException invalid) {
        return failure(HttpStatus.BAD_REQUEST, invalid.getMessage());
    }

    @ExceptionHandler(SimulatorException.class)
    void handleSimulatorException(SimulatorException exception, HttpServletResponse response) throws IOException {
        response.sendError(exception.getStatus().value(), exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of(TableApi.ERROR, Map.of(TableApi.MESSAGE, message), TableApi.STATUS, TableApi.FAILURE));
    }
}
