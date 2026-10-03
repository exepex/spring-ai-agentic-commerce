package io.github.exepex.commerce.platform.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ProblemDetailsExceptionHandlerTest {

    static class PaymentDeclinedException extends CommerceException {

        PaymentDeclinedException(Map<String, Object> properties) {
            super(HttpStatus.PAYMENT_REQUIRED, "Your card was declined.", properties);
        }
    }

    @RestController
    static class Failing {

        @GetMapping("/declined")
        void declined() {
            var properties = new LinkedHashMap<String, Object>();
            properties.put("orderId", "8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001");
            throw new PaymentDeclinedException(properties);
        }
    }

    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new Failing())
            .setControllerAdvice(new ProblemDetailsExceptionHandler())
            .build();

    @Test
    void aServiceFailureIsAnsweredAsAProblemWithItsStatusDetailAndProperties() throws Exception {
        mvc.perform(get("/declined"))
                .andExpect(status().isPaymentRequired())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.title").value("Payment Required"))
                .andExpect(jsonPath("$.detail").value("Your card was declined."))
                .andExpect(jsonPath("$.instance").value("/declined"))
                .andExpect(jsonPath("$.orderId").value("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001"));
    }
}
