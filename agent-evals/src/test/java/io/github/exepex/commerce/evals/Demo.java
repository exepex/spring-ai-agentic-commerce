package io.github.exepex.commerce.evals;

import static org.awaitility.Awaitility.await;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Drives the running demo through the same API the UI uses (by default nginx on localhost:8080). */
final class Demo {

    static final String HEADLAMP = "8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0004";
    static final String SHOE_42 = "8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001";
    static final String ORDER_EXCEPTIONS_AGENT = "order-exceptions-agent";
    static final String OPERATOR = "evals@trailhead.example";

    private static final Duration AGENT_TIMEOUT = Duration.ofMinutes(4);

    private final RestClient api = RestClient.builder()
            .baseUrl(System.getProperty("evals.baseUrl", "http://localhost:8080/svc"))
            .requestFactory(httpOneOneWithTimeouts())
            .build();

    String placeOrder(String customerEmail, String productId) {
        return api.post().uri("/orders/api/orders")
                .body(Map.of("customerEmail", customerEmail, "lines", List.of(Map.of("productId", productId, "quantity", 1))))
                .retrieve().body(JsonNode.class).path("id").asString();
    }

    List<JsonNode> ordersOf(String customerEmail) {
        return list(api.get().uri("/orders/api/orders?customerEmail={email}", customerEmail).retrieve().body(JsonNode.class));
    }

    JsonNode order(String orderId) {
        return api.get().uri("/orders/api/orders/{id}", orderId).retrieve().body(JsonNode.class);
    }

    JsonNode payment(String orderId) {
        return api.get().uri("/payments/api/payments/{id}", orderId).retrieve().body(JsonNode.class);
    }

    /** Writes off one unit more than is available, so the newest order for the product becomes a stock-out. */
    int causeStockOut(String productId) {
        JsonNode product = api.get().uri("/catalog/api/products/{id}", productId).retrieve().body(JsonNode.class);
        int units = product.path("available").asInt() + 1;
        adjustStock(productId, -units, "eval: damaged in warehouse");
        return units;
    }

    void restock(String productId, int units) {
        adjustStock(productId, units, "eval: restock");
    }

    List<JsonNode> timeline(String orderId) {
        return list(api.get().uri("/governance/api/orders/{id}/timeline", orderId).retrieve().body(JsonNode.class));
    }

    List<JsonNode> refundRequests(String orderId) {
        return list(api.get().uri("/governance/api/refund-requests?orderId={id}", orderId).retrieve().body(JsonNode.class));
    }

    List<JsonNode> escalationsFor(String orderId) {
        return list(api.get().uri("/governance/api/escalations").retrieve().body(JsonNode.class)).stream()
                .filter(escalation -> orderId.equals(escalation.path("orderId").asString()))
                .toList();
    }

    List<JsonNode> notifications(String orderId) {
        return list(api.get().uri("/governance/api/notifications?orderId={id}", orderId).retrieve().body(JsonNode.class));
    }

    /** Takes the order's escalation, as a person would, then retries the refund: only the assignee may. */
    JsonNode retryRefund(String orderId, String refundRequestId) {
        for (JsonNode escalation : escalationsFor(orderId)) {
            if ("OPEN".equals(escalation.path("status").asString())) {
                api.post().uri("/governance/api/escalations/{id}/assign", escalation.path("id").asString())
                        .body(Map.of("by", OPERATOR)).retrieve().toBodilessEntity();
            }
        }
        return api.post().uri("/governance/api/refund-requests/{id}/retry", refundRequestId)
                .body(Map.of("by", OPERATOR)).retrieve().body(JsonNode.class);
    }

    void setAgentEnabled(String agentId, boolean enabled) {
        api.put().uri("/agents/api/agents/{id}", agentId).body(Map.of("enabled", enabled, "by", OPERATOR))
                .retrieve().toBodilessEntity();
    }

    void setPaymentOutage(boolean active) {
        api.put().uri("/payments/api/admin/simulated-outage").body(Map.of("active", active)).retrieve().toBodilessEntity();
    }

    JsonNode chat(String conversationId, String customerEmail, String message) {
        return api.post().uri("/agents/api/assistant/chat")
                .body(Map.of("conversationId", conversationId, "customerEmail", customerEmail, "message", message))
                .retrieve().body(JsonNode.class);
    }

    /** Waits until the order's timeline shows an event matching the predicate, then returns the whole timeline. */
    List<JsonNode> awaitTimeline(String orderId, Predicate<JsonNode> event) {
        await().atMost(AGENT_TIMEOUT).pollInterval(Duration.ofSeconds(2))
                .until(() -> timeline(orderId).stream().anyMatch(event));
        return timeline(orderId);
    }

    /** The agent records its decision last, so this waits for the whole run to finish. */
    List<JsonNode> awaitAgentFinished(String orderId) {
        return awaitTimeline(orderId, event -> is(event, ORDER_EXCEPTIONS_AGENT, "decision")
                || is(event, ORDER_EXCEPTIONS_AGENT, "escalate_to_human"));
    }

    static boolean is(JsonNode event, String actor, String action) {
        return actor.equals(event.path("actor").asString()) && action.equals(event.path("action").asString());
    }

    static long count(List<JsonNode> timeline, String actor, String action, String outcome) {
        return timeline.stream()
                .filter(event -> is(event, actor, action) && outcome.equals(event.path("outcome").asString()))
                .count();
    }

    static String newCustomer() {
        return "eval-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private void adjustStock(String productId, int delta, String reason) {
        api.post().uri("/catalog/api/products/{id}/stock-adjustments", productId)
                .body(Map.of("delta", delta, "reason", reason)).retrieve().toBodilessEntity();
    }

    /**
     * HTTP/1.1, because the JDK client otherwise offers an HTTP/2 upgrade that the UI's development proxy never
     * answers. A chat with the model can take a while, but no call may hang forever.
     */
    private static JdkClientHttpRequestFactory httpOneOneWithTimeouts() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
        requestFactory.setReadTimeout(Duration.ofMinutes(3));
        return requestFactory;
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }
}
