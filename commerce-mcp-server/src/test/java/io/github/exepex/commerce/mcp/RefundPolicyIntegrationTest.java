package io.github.exepex.commerce.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The refund policy, enforced by the server whatever the agent asks: refunds above the approval limit wait for a human,
 * even when split or sent at once, each idempotency key pays out at most once, and only its own agent may reuse it.
 */
class RefundPolicyIntegrationTest extends McpServerTestSupport {

    @Test
    void aRefundWithinTheLimitRunsOnceEvenWhenTheAgentRetriesIt() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        Map<String, Object> refund = Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId);

        String first = text(call(incidentAgent, "issue_refund", refund));
        String second = text(call(incidentAgent, "issue_refund", refund));

        assertThat((String) JsonPath.read(first, "$.status")).isEqualTo("EXECUTED");
        assertThat((String) JsonPath.read(second, "$.refundRequestId")).isEqualTo(JsonPath.read(first, "$.refundRequestId"));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Bearer dev-internal-api-token")));
    }

    @Test
    void aRefundAboveTheLimitWaitsForAHumanAndRunsOnceApproved() {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        stubRefundSucceeds(orderId);

        String result = text(call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 129.90, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId)));

        assertThat((String) JsonPath.read(result, "$.status")).isEqualTo("PENDING_APPROVAL");
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));

        String approved = rest().post().uri("/api/refund-requests/{id}/approve", (String) JsonPath.read(result, "$.refundRequestId"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("by", "ops@example.com", "note", "Goodwill: our stock error"))
                .retrieve().body(String.class);
        assertThat((String) JsonPath.read(approved, "$.status")).isEqualTo("EXECUTED");
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds"))
                .withRequestBody(matchingJsonPath("$.idempotencyKey", equalTo("refund-" + orderId))));
    }

    @Test
    void splittingARefundDoesNotGetAroundTheApprovalLimit() {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        stubRefundSucceeds(orderId);

        String first = text(call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 65, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-1")));
        String second = text(call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 64.90, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-2")));

        assertThat((String) JsonPath.read(first, "$.status")).isEqualTo("EXECUTED");
        assertThat((String) JsonPath.read(second, "$.status")).isEqualTo("PENDING_APPROVAL");
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
    }

    @Test
    void twoRefundsAtOnceCannotTogetherStayUnderTheApprovalLimit() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(okJson("""
                {"id": "%s", "orderId": "%s", "amount": 129.90, "refundedAmount": 0, "refundable": 129.90, "currency": "EUR",
                 "status": "SUCCEEDED", "refunds": []}""".formatted(UUID.randomUUID(), orderId)).withFixedDelay(300)));
        stubRefundSucceeds(orderId);

        runTogether(() -> refundSixty(incidentAgent, orderId, "a"), () -> refundSixty(assistant, orderId, "b"));

        String requests = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        List<String> statuses = JsonPath.read(requests, "$[*].status");
        assertThat(statuses).containsExactlyInAnyOrder("EXECUTED", "PENDING_APPROVAL");
    }

    @Test
    void aRefundReasonTooLongForThePaymentServiceIsRefusedBeforeAnyMoneyMoves() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);

        McpSchema.CallToolResult refused = call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "x".repeat(501), "idempotencyKey", "refund-" + orderId));

        assertThat(refused.isError()).isTrue();
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
    }

    @Test
    void theSameRefundSentTwiceAtOnceGivesOneRequestAndBothCallsSucceed() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(okJson("""
                {"id": "%s", "orderId": "%s", "amount": 39.50, "refundedAmount": 0, "refundable": 39.50, "currency": "EUR",
                 "status": "SUCCEEDED", "refunds": []}""".formatted(UUID.randomUUID(), orderId)).withFixedDelay(300)));
        stubRefundSucceeds(orderId);

        List<Integer> succeeded = runTogether(
                () -> refundAll(incidentAgent, orderId), () -> refundAll(incidentAgent, orderId));

        assertThat(succeeded).containsExactly(1, 1);
        String requests = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        assertThat((List<?>) JsonPath.read(requests, "$")).hasSize(1);
    }

    @Test
    void anApprovalNoteTooLongToStoreIsRefusedBeforeAnyMoneyMoves() {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        stubRefundSucceeds(orderId);
        String requestId = JsonPath.read(text(call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 129.90, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId))), "$.refundRequestId");

        int status = rest().post().uri("/api/refund-requests/{id}/approve", requestId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("by", "ops@example.com", "note", "x".repeat(1001)))
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(422);
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
        String requests = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        assertThat((String) JsonPath.read(requests, "$[0].status")).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void anIdempotencyKeyTooLongToStoreIsRefused() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);

        McpSchema.CallToolResult refused = call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "item out of stock", "idempotencyKey", "k".repeat(201)));

        assertThat(refused.isError()).isTrue();
        SERVICES.verify(0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
    }

    @Test
    void twoPeopleDecidingOnTheSameRefundAtOnceCannotBothAct() throws Exception {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(201)
                .withFixedDelay(500).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": "%s", "amount": 129.90, "reason": "item out of stock", "providerReference": "re_test"}"""
                        .formatted(UUID.randomUUID()))));
        String requestId = JsonPath.read(text(call(incidentAgent, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 129.90, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId))), "$.refundRequestId");

        List<Integer> statuses = runTogether(
                () -> decide(requestId, "approve"), () -> decide(requestId, "reject"));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String decided = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        String status = JsonPath.read(decided, "$[0].status");
        SERVICES.verify(status.equals("EXECUTED") ? 1 : 0, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
        assertThat(status).isIn("EXECUTED", "REJECTED");
    }

    @Test
    void aRefundThatFailedBecausePaymentsWereDownSucceedsWhenRetriedWithTheSameKey() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        Map<String, Object> refund = Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId);

        String failed = text(call(incidentAgent, "issue_refund", refund));
        assertThat((String) JsonPath.read(failed, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(failed, "$.message")).contains("safe");

        stubRefundSucceeds(orderId);
        assertThat((String) JsonPath.read(text(call(incidentAgent, "issue_refund", refund)), "$.status")).isEqualTo("EXECUTED");
    }

    @Test
    void anAgentCannotRepeatARefundKeyAnotherAgentAskedFor() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        Map<String, Object> stockOutRefund = Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-stockout");
        call(incidentAgent, "issue_refund", stockOutRefund);
        stubRefundSucceeds(orderId);

        McpSchema.CallToolResult reused = call(assistant, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-stockout",
                "customerEmail", "ada@example.com"));

        assertThat(reused.isError()).isTrue();
        assertThat(text(reused)).contains("another agent");
        String requests = rest().get().uri("/api/refund-requests?orderId={id}", orderId).retrieve().body(String.class);
        assertThat((String) JsonPath.read(requests, "$[0].status")).isEqualTo("FAILED");
    }

    /** Asks for the full €39.50 refund with one fixed key and returns 1 when the call succeeded. */
    private static int refundAll(McpSyncClient client, UUID orderId) {
        McpSchema.CallToolResult result = call(client, "issue_refund", Map.of("orderId", orderId.toString(),
                "amount", 39.50, "reason", "item out of stock", "idempotencyKey", "refund-" + orderId));
        return Boolean.TRUE.equals(result.isError()) ? 0 : 1;
    }

    /** Asks for a €60 refund and returns 1 when the tool call succeeded, 0 when it was refused. */
    private static int refundSixty(McpSyncClient client, UUID orderId, String key) {
        McpSchema.CallToolResult result = call(client, "issue_refund", Map.of("orderId", orderId.toString(), "amount", 60,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId + "-" + key,
                "customerEmail", "ada@example.com"));
        return Boolean.TRUE.equals(result.isError()) ? 0 : 1;
    }

    private int decide(String requestId, String decision) {
        return rest().post().uri("/api/refund-requests/{id}/" + decision, requestId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("by", "ops@example.com"))
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
