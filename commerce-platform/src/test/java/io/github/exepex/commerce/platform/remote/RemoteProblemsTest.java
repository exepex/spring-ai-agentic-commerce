package io.github.exepex.commerce.platform.remote;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

class RemoteProblemsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void theOtherServicesOwnExplanationIsUsed() {
        var refused = refusal("{\"status\": 409, \"detail\": \"Only 2 left\"}");

        assertThat(RemoteProblems.detailOf(refused, "Conflict")).isEqualTo("Only 2 left");
    }

    @Test
    void aRefusalWithoutAReadableProblemFallsBack() {
        assertThat(RemoteProblems.detailOf(refusal("not json"), "Conflict")).isEqualTo("Conflict");
        assertThat(RemoteProblems.problemOf(refusal("not json"))).isEmpty();
    }

    @Test
    void aProblemWithoutADetailFallsBack() {
        assertThat(RemoteProblems.detailOf(refusal("{\"status\": 409}"), "Conflict")).isEqualTo("Conflict");
    }

    private static HttpClientErrorException refusal(String body) {
        var refused = HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", new HttpHeaders(),
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        refused.setBodyConvertFunction(type -> JSON.readValue(body, ProblemDetail.class));
        return refused;
    }
}
