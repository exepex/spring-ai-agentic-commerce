package io.github.exepex.commerce.platform.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class DatabaseUnavailableExceptionHandlerTest {

    @RestController
    static class Overloaded {

        @GetMapping("/no-connection")
        void noConnection() {
            throw new CannotCreateTransactionException("Connection is not available, request timed out after 5000ms");
        }

        @GetMapping("/database-down")
        void databaseDown() {
            throw new DataAccessResourceFailureException("Connection refused");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Overloaded())
            .setControllerAdvice(new DatabaseUnavailableExceptionHandler(), new ProblemDetailsExceptionHandler())
            .build();

    @Test
    void aServiceThatCannotReachItsDatabaseInTimeAsksTheCallerToTryAgain() throws Exception {
        for (var path : new String[] {"/no-connection", "/database-down"}) {
            mvc.perform(get(path))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "1"))
                    .andExpect(jsonPath("$.detail").value(DatabaseUnavailableExceptionHandler.BUSY));
        }
    }
}
