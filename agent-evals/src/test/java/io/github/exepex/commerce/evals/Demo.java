package io.github.exepex.commerce.evals;

import static org.awaitility.Awaitility.await;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Drives the running demo through the same API the UI uses (by default nginx on localhost:8080). */
final class Demo {

    static final String HEADLAMP = "8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0004";
    static final String SHOE_42 = "8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001";
    static final String INCIDENT_AGENT = "incident-agent";
    static final String OPERATOR = "evals@trailhead.example";
    private static final String STOCK_OUT_TOPIC = "inventory.stock-out";

    /** A case reaches ServiceNow, is worked and read back within a few poll intervals and one agent run. */
    private static final Duration AGENT_TIMEOUT = Duration.ofMinutes(6);

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

    List<JsonNode> casesOf(String orderId) {
        return list(api.get().uri("/governance/api/cases?orderId={id}", orderId).retrieve().body(JsonNode.class));
    }

    List<JsonNode> notifications(String orderId) {
        return list(api.get().uri("/governance/api/notifications?orderId={id}", orderId).retrieve().body(JsonNode.class));
    }

    /** Retries a failed refund from the operations console, as any operator may. */
    JsonNode retryRefund(String refundRequestId) {
        return api.post().uri("/governance/api/refund-requests/{id}/retry", refundRequestId)
                .body(Map.of("by", OPERATOR)).retrieve().body(JsonNode.class);
    }

    /**
     * Delivers the stock-out that named the order a second time, as Kafka does when a consumer stops before committing
     * its offset. Kafka is reached at {@code evals.kafka} (by default the demo's {@code localhost:9092}).
     */
    void redeliverStockOutOf(String orderId) {
        String bootstrapServers = System.getProperty("evals.kafka", "localhost:9092");
        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "evals-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        ConsumerRecord<String, String> stockOut = null;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of(STOCK_OUT_TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (stockOut == null && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
                    if (record.value().contains(orderId)) {
                        stockOut = record;
                    }
                }
            }
        }
        if (stockOut == null) {
            throw new IllegalStateException("No stock-out names order " + orderId);
        }
        Map<String, Object> producerProperties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
            producer.send(new ProducerRecord<>(STOCK_OUT_TOPIC, stockOut.key(), stockOut.value()));
        }
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

    /**
     * Waits until the order's case is finished in ServiceNow: resolved, or with a team. The poller reads that back
     * after the agent's run, so the whole run is in the timeline by then.
     */
    JsonNode awaitCaseFinished(String orderId) {
        await().atMost(AGENT_TIMEOUT).pollInterval(Duration.ofSeconds(3)).until(() -> casesOf(orderId).stream()
                .anyMatch(supportCase -> Set.of("RESOLVED", "WITH_TEAM").contains(supportCase.path("status").asString())));
        return casesOf(orderId).getFirst();
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
