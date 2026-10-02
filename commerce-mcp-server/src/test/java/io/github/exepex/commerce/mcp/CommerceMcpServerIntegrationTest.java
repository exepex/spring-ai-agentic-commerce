package io.github.exepex.commerce.mcp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Talks to the real server through a real MCP client over streamable HTTP, as the agents do. Postgres and Kafka run
 * in containers; one WireMock server stands in for the four commerce services.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CommerceMcpServerIntegrationTest {

    private static final String ASSISTANT_TOKEN = "dev-shopping-assistant-token";
    private static final String EXCEPTIONS_AGENT_TOKEN = "dev-order-exceptions-agent-token";

    private static final WireMockServer SERVICES = startWireMock();

    @LocalServerPort
    private int port;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    private McpSyncClient assistant;
    private McpSyncClient exceptionsAgent;

    @DynamicPropertySource
    static void pointAtWireMock(DynamicPropertyRegistry registry) {
        for (String service : List.of("catalog", "order", "payment", "shipping")) {
            registry.add("spring.http.serviceclient." + service + ".base-url", SERVICES::baseUrl);
        }
    }

    @BeforeEach
    void connectTheAgents() {
        SERVICES.resetAll();
        assistant = connect(ASSISTANT_TOKEN);
        exceptionsAgent = connect(EXCEPTIONS_AGENT_TOKEN);
    }

    @AfterEach
    void disconnect() {
        assistant.closeGracefully();
        exceptionsAgent.closeGracefully();
    }

    @AfterAll
    static void stopWireMock() {
        SERVICES.stop();
    }

    @Test
    void refusesACallerWithoutAnAgentToken() {
        HttpStatusCode status = RestClient.create("http://localhost:" + port).post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON).body("{}")
                .exchange((request, response) -> response.getStatusCode());
        assertThat(status.value()).isEqualTo(401);
    }

    @Test
    void offersTheShopTools() {
        assertThat(assistant.listTools().tools()).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(
                "search_products", "find_customer_orders", "get_order", "track_shipment", "propose_order",
                "cancel_order", "issue_refund", "notify_customer", "escalate_to_human");
    }

    @Test
    void anAgentCannotCallAToolItIsNotPermittedToUse() {
        McpSchema.CallToolResult result = call(exceptionsAgent, "propose_order", Map.of("customerEmail", "ada@example.com",
                "lines", List.of(Map.of("productId", UUID.randomUUID().toString(), "quantity", 1))));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("not permitted to call propose_order");
        assertThat(rest().get().uri("/api/audit-events").retrieve().body(String.class))
                .contains("\"outcome\":\"DENIED\"");
    }

    @Test
    void theShoppingAssistantCannotSeeAnotherCustomersOrder() {
        UUID graceOrder = stubOrder("grace@example.com", "39.50");

        McpSchema.CallToolResult result = call(assistant, "get_order",
                Map.of("orderId", graceOrder.toString(), "customerEmail", "ada@example.com"));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("does not belong to the customer in this conversation");
    }

    @Test
    void aRefundWithinTheLimitRunsOnceEvenWhenTheAgentRetriesIt() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        stubRefundSucceeds(orderId);
        Map<String, Object> refund = Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId);

        String first = text(call(exceptionsAgent, "issue_refund", refund));
        String second = text(call(exceptionsAgent, "issue_refund", refund));

        assertThat((String) JsonPath.read(first, "$.status")).isEqualTo("EXECUTED");
        assertThat((String) JsonPath.read(second, "$.refundRequestId")).isEqualTo(JsonPath.read(first, "$.refundRequestId"));
        SERVICES.verify(1, postRequestedFor(urlEqualTo("/api/payments/" + orderId + "/refunds")));
    }

    @Test
    void aRefundAboveTheLimitWaitsForAHumanAndRunsOnceApproved() {
        UUID orderId = stubOrder("ada@example.com", "129.90");
        stubRefundSucceeds(orderId);

        String result = text(call(exceptionsAgent, "issue_refund", Map.of("orderId", orderId.toString(),
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
    void aRefundThatFailedBecausePaymentsWereDownSucceedsWhenRetriedWithTheSameKey() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(503)));
        Map<String, Object> refund = Map.of("orderId", orderId.toString(), "amount", 39.50,
                "reason", "item out of stock", "idempotencyKey", "refund-" + orderId);

        String failed = text(call(exceptionsAgent, "issue_refund", refund));
        assertThat((String) JsonPath.read(failed, "$.status")).isEqualTo("FAILED");
        assertThat((String) JsonPath.read(failed, "$.message")).contains("safe");

        stubRefundSucceeds(orderId);
        assertThat((String) JsonPath.read(text(call(exceptionsAgent, "issue_refund", refund)), "$.status")).isEqualTo("EXECUTED");
    }

    @Test
    void anOrderLookupSaysSoWhenThePaymentServiceIsDownInsteadOfShowingNoPayment() {
        UUID orderId = stubOrder("ada@example.com", "39.50");
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(aResponse().withStatus(503)));

        String order = text(call(exceptionsAgent, "get_order", Map.of("orderId", orderId.toString())));

        assertThat((String) JsonPath.read(order, "$.payment.status")).isEqualTo("UNKNOWN");
        assertThat((String) JsonPath.read(order, "$.payment.message")).contains("payment service is unavailable");
    }

    @Test
    void aStockOutAppearsOnTheTimelineOfEveryAffectedOrderAtTheTimeItHappened() {
        UUID orderId = UUID.randomUUID();

        kafka.send("inventory.stock-out", "product", """
                {"eventId": "%s", "occurredAt": "2026-10-02T09:15:00Z", "productId": "%s", "sku": "RUN-SHOE-BLUE-43",
                 "onHand": 1, "reserved": 2, "shortfall": 1, "reason": "damaged in warehouse", "affectedOrderIds": ["%s"]}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), orderId)).join();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(rest().get().uri("/api/orders/{orderId}/timeline", orderId).retrieve().body(String.class))
                        .contains("STOCK_OUT", "damaged in warehouse", "2026-10-02T09:15:00Z"));
    }

    @Test
    void aDeclinedConfirmationShowsOnTheTimelineOfTheOrderItFailed() {
        UUID productId = UUID.randomUUID();
        UUID failedOrderId = UUID.randomUUID();
        SERVICES.stubFor(get("/api/products").willReturn(okJson("""
                [{"id": "%s", "sku": "HEADLAMP-400", "name": "Headlamp", "description": "", "price": 39.50,
                  "currency": "EUR", "onHand": 5, "reserved": 0, "available": 5}]""".formatted(productId))));
        SERVICES.stubFor(post("/api/orders").willReturn(aResponse().withStatus(402)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                        {"status": 402, "detail": "Your card was declined.", "orderId": "%s"}""".formatted(failedOrderId))));
        String proposal = text(call(assistant, "propose_order", Map.of("customerEmail", "ada@example.com",
                "lines", List.of(Map.of("productId", productId.toString(), "quantity", 1)))));

        String confirmed = rest().post().uri("/api/order-proposals/{id}/confirm", (String) JsonPath.read(proposal, "$.id"))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("paymentMethod", "pm_card_chargeDeclined"))
                .retrieve().body(String.class);

        assertThat((String) JsonPath.read(confirmed, "$.status")).isEqualTo("FAILED");
        assertThat(rest().get().uri("/api/orders/{orderId}/timeline", failedOrderId).retrieve().body(String.class))
                .contains("confirm_order", "FAILED", "Your card was declined.");
    }

    private McpSyncClient connect(String token) {
        McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                        .build())
                .requestTimeout(Duration.ofSeconds(20))
                .build();
        client.initialize();
        return client;
    }

    private static McpSchema.CallToolResult call(McpSyncClient client, String tool, Map<String, Object> arguments) {
        return client.callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    private RestClient rest() {
        return RestClient.create("http://localhost:" + port);
    }

    private static UUID stubOrder(String customerEmail, String total) {
        UUID orderId = UUID.randomUUID();
        SERVICES.stubFor(get("/api/orders/" + orderId).willReturn(okJson("""
                {"id": "%s", "customerEmail": "%s", "status": "CONFIRMED", "total": %s, "currency": "EUR",
                 "createdAt": "2026-10-02T10:00:00Z", "lines": []}""".formatted(orderId, customerEmail, total))));
        SERVICES.stubFor(get("/api/payments/" + orderId).willReturn(okJson("""
                {"id": "%s", "orderId": "%s", "amount": %s, "refundedAmount": 0, "refundable": %s, "currency": "EUR",
                 "status": "SUCCEEDED", "refunds": []}""".formatted(UUID.randomUUID(), orderId, total, total))));
        return orderId;
    }

    private static void stubRefundSucceeds(UUID orderId) {
        SERVICES.stubFor(post("/api/payments/" + orderId + "/refunds").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"id": "%s", "amount": 39.50, "reason": "item out of stock", "providerReference": "re_test"}"""
                        .formatted(UUID.randomUUID()))));
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }
}
