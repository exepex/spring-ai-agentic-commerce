package io.github.exepex.commerce.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * A refund the card processor fails after accepting it is marked failed and opens one case, however often the failure
 * is announced; no slower answer from the payment service undoes it, and a refund that did get through counts as paid.
 */
class FailedRefundCaseIntegrationTest extends CaseTestSupport {

    @Test
    void aRefundTheProcessorFailsAfterwardsIsMarkedFailedAndOpensOneCase() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(), "amount", 39.50, "reason",
                "item out of stock", "idempotencyKey", "refund-" + orderId));
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
        String refundFailed = """
                {"eventId": "%s", "type": "REFUND_FAILED", "orderId": "%s", "idempotencyKey": "refund-%s",
                 "amount": 39.50, "currency": "EUR", "occurredAt": "2026-10-02T10:30:00Z"}"""
                .formatted(UUID.randomUUID(), orderId, orderId);

        kafka.send("payment.events", orderId.toString(), refundFailed).join();
        kafka.send("payment.events", orderId.toString(), refundFailed).join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(refundStatus(orderId)).isEqualTo("FAILED"));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat((List<String>) JsonPath.read(timeline(orderId), "$[?(@.action == 'refund_failed')].actor"))
                    .containsExactly("payment-service");
            assertThat((List<String>) JsonPath.read(cases(orderId), "$[*].type")).containsExactly("REFUND_FAILED");
        });
    }

    @Test
    void aRefundStillRecordedAsFailedThatTheProcessorFailsIsRecordedAndOpensOneCase() {
        // This server stopped after the payment service took the refund, so the request still shows FAILED.
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(), "amount", 39.50, "reason",
                "item out of stock", "idempotencyKey", "refund-" + orderId));
        assertThat(refundStatus(orderId)).isEqualTo("FAILED");
        String refundFailed = """
                {"eventId": "%s", "type": "REFUND_FAILED", "orderId": "%s", "idempotencyKey": "refund-%s",
                 "amount": 39.50, "currency": "EUR", "occurredAt": "2026-10-02T10:30:00Z"}"""
                .formatted(UUID.randomUUID(), orderId, orderId);

        kafka.send("payment.events", orderId.toString(), refundFailed).join();
        kafka.send("payment.events", orderId.toString(), refundFailed).join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat((List<String>) JsonPath.read(cases(orderId), "$[*].type")).containsExactly("REFUND_FAILED"));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat((List<String>) JsonPath.read(timeline(orderId), "$[?(@.action == 'refund_failed')].actor"))
                    .containsExactly("payment-service");
            assertThat((List<?>) JsonPath.read(outgoingFor(orderId), "$[0].unsentNotes")).isEmpty();
        });
        assertThat((String) JsonPath.read(rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve()
                .body(String.class), "$[0].failure")).contains("card processor reported");
    }

    @Test
    void aSlowAnswerFromThePaymentServiceDoesNotOverwriteAFailureTheProcessorReportedMeanwhile() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withFixedDelay(4000)
                .withBody("{\"id\": \"%s\", \"amount\": 39.50, \"providerReference\": \"re_slow\"}"
                        .formatted(UUID.randomUUID()))));
        CompletableFuture<McpSchema.CallToolResult> refund = CompletableFuture.supplyAsync(() -> call(incidentAgent,
                "issue_refund", Map.of("orderId", orderId.toString(), "amount", 39.50, "reason", "item out of stock",
                        "idempotencyKey", "refund-" + orderId)));
        await().atMost(Duration.ofSeconds(3)).until(() -> rest().get().uri("/api/refund-requests?orderId={id}", orderId)
                .retrieve().body(String.class).contains("refund-" + orderId));

        kafka.send("payment.events", orderId.toString(), """
                {"eventId": "%s", "type": "REFUND_FAILED", "orderId": "%s", "idempotencyKey": "refund-%s",
                 "amount": 39.50, "currency": "EUR", "occurredAt": "2026-10-02T10:30:00Z"}"""
                .formatted(UUID.randomUUID(), orderId, orderId)).join();
        await().atMost(Duration.ofSeconds(3)).until(() -> timeline(orderId).contains("refund_failed"));
        refund.join();

        assertThat(refundStatus(orderId)).isEqualTo("FAILED");
        assertThat((List<String>) JsonPath.read(cases(orderId), "$[*].type")).containsExactly("REFUND_FAILED");
        assertThat((List<String>) JsonPath.read(timeline(orderId), "$[?(@.action == 'issue_refund')].outcome"))
                .filteredOn("SUCCEEDED"::equals).isEmpty();
    }

    @Test
    void whenTwoPeopleRetryARefundAtOnceAndOnlyOneGetsThroughTheRefundCountsAsPaid() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        String refundRequestId = JsonPath.read(text(call(incidentAgent, "issue_refund", Map.of("orderId",
                orderId.toString(), "amount", 39.50, "reason", "item out of stock", "idempotencyKey",
                "refund-" + orderId))), "$.refundRequestId");
        // The first retry gets through, but its answer is slow; the second fails at once and is saved first.
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").inScenario("retries").whenScenarioStateIs(STARTED)
                .willSetStateTo("second").willReturn(aResponse().withStatus(201).withFixedDelay(2000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\": \"%s\", \"amount\": 39.50, \"providerReference\": \"re_ok\"}"
                                .formatted(UUID.randomUUID()))));
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").inScenario("retries").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(503)));

        CompletableFuture<Integer> slowSuccess = CompletableFuture.supplyAsync(() -> retry(refundRequestId, "ana@trailhead.example"));
        await().atMost(Duration.ofSeconds(2)).until(() -> SERVICES.getAllScenarios().getScenarios().stream()
                .anyMatch(scenario -> "second".equals(scenario.getState())));
        int fastFailure = retry(refundRequestId, "ben@trailhead.example");

        assertThat(List.of(slowSuccess.join(), fastFailure)).containsExactly(200, 200);
        assertThat(refundStatus(orderId)).isEqualTo("EXECUTED");
    }
}
