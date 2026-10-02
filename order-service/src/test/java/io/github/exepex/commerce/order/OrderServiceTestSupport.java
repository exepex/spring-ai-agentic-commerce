package io.github.exepex.commerce.order;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;

/**
 * What the order service's integration tests share: the real service against Postgres and Kafka, one WireMock server
 * standing in for both the catalog and the payment service over real HTTP (their paths do not overlap), and helpers.
 * The reconciler never runs on its own schedule here; tests call it directly, and every order counts as stalled at
 * once.
 */
@SpringBootTest(properties = {"commerce.reconciliation.interval=1h", "commerce.reconciliation.settle-after=0s"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class OrderServiceTestSupport {

    protected static final UUID SHOE = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001");
    protected static final UUID HEADLAMP = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0004");
    protected static final UUID UNKNOWN_PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000000");

    protected static final WireMockServer DEPENDENCIES = startWireMock();

    @Autowired
    protected MockMvcTester mockMvc;

    @Autowired
    private KafkaContainer kafka;

    @DynamicPropertySource
    static void pointAtWireMock(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.catalog.base-url", DEPENDENCIES::baseUrl);
        registry.add("spring.http.serviceclient.payment.base-url", DEPENDENCIES::baseUrl);
    }

    @BeforeEach
    void stubTheDependencies() {
        DEPENDENCIES.resetAll();
        stubProduct(SHOE, "RUN-SHOE-BLUE-42", "Trail running shoe, blue, EU 42", "129.90");
        stubProduct(HEADLAMP, "HEADLAMP-400", "Headlamp, 400 lumen", "39.50");
        DEPENDENCIES.stubFor(get("/api/products/" + UNKNOWN_PRODUCT).willReturn(problem(HttpStatus.NOT_FOUND, "no such product")));
        DEPENDENCIES.stubFor(post(urlMatching("/api/products/.+/reservations")).willReturn(aResponse().withStatus(201)));
        DEPENDENCIES.stubFor(delete(urlMatching("/api/orders/.+/reservations")).willReturn(aResponse().withStatus(204)));
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(aResponse().withStatus(201)));
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        // Every test class shares this server and the cached application context that points at it.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        return server;
    }

    protected static void stubProduct(UUID productId, String sku, String name, String price) {
        DEPENDENCIES.stubFor(get("/api/products/" + productId).willReturn(okJson("""
                {"id": "%s", "sku": "%s", "name": "%s", "price": %s, "currency": "EUR", "available": 10}"""
                .formatted(productId, sku, name, price))));
    }

    protected static ResponseDefinitionBuilder problem(HttpStatus status, String detail) {
        return aResponse().withStatus(status.value())
                .withHeader("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .withBody("""
                        {"status": %d, "detail": "%s"}""".formatted(status.value(), detail));
    }

    protected MvcTestResult placeOrder(String customerEmail, Object... productsAndQuantities) {
        return placeOrderWithId(null, customerEmail, productsAndQuantities);
    }

    /** Places the order under an id the caller chose, so asking again places nothing new. */
    protected MvcTestResult placeOrderWithId(UUID orderId, String customerEmail, Object... productsAndQuantities) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < productsAndQuantities.length; index += 2) {
            lines.add("""
                    {"productId": "%s", "quantity": %d}""".formatted(productsAndQuantities[index],
                    productsAndQuantities[index + 1]));
        }
        return mockMvc.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId": %s, "customerEmail": "%s", "lines": [%s]}""".formatted(
                        orderId == null ? "null" : "\"" + orderId + "\"", customerEmail, String.join(", ", lines)))
                .exchange();
    }

    protected String placedOrderOf(String customerEmail) throws Exception {
        List<String> placed = JsonPath.read(mockMvc.get().uri("/api/orders?customerEmail={email}", customerEmail).exchange()
                .getResponse().getContentAsString(), "$[?(@.status == 'PLACED')].id");
        return placed.isEmpty() ? null : placed.getFirst();
    }

    protected MvcTestResult cancel(String orderId, String reason) {
        return mockMvc.post().uri("/api/orders/{orderId}/cancellation", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s"}""".formatted(reason))
                .exchange();
    }

    protected static String orderIdOf(MvcTestResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected List<String> orderEventTypesFor(String orderId, int expectedEvents) {
        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of("order.events"));
            List<String> types = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (types.size() < expectedEvents && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2))) {
                    if (orderId.equals(record.key())) {
                        types.add(JsonPath.read(record.value(), "$.type"));
                    }
                }
            }
            return types;
        }
    }
}
