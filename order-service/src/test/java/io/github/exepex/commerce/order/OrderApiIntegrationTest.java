package io.github.exepex.commerce.order;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Runs the real service against Postgres and Kafka. One WireMock server stands in for both the catalog and the
 * payment service over real HTTP; their paths do not overlap.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OrderApiIntegrationTest {

    private static final UUID SHOE = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001");
    private static final UUID HEADLAMP = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0004");
    private static final UUID UNKNOWN_PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final WireMockServer DEPENDENCIES = startWireMock();

    @Autowired
    private MockMvcTester mockMvc;

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

    @AfterAll
    static void stopWireMock() {
        DEPENDENCIES.stop();
    }

    @Test
    void placesAnOrderReservingEveryLineChargingTheTotalAndAnnouncingIt() throws Exception {
        MvcTestResult placed = placeOrder("ada@example.com", SHOE, 2, HEADLAMP, 1);

        assertThat(placed).hasStatus(HttpStatus.CREATED);
        assertThat(placed).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(placed).bodyJson().extractingPath("$.total").isEqualTo(299.30);
        String orderId = orderIdOf(placed);
        DEPENDENCIES.verify(postRequestedFor(urlEqualTo("/api/products/" + SHOE + "/reservations"))
                .withRequestBody(equalToJson("""
                        {"orderId": "%s", "quantity": 2}""".formatted(orderId))));
        DEPENDENCIES.verify(postRequestedFor(urlEqualTo("/api/payments")).withRequestBody(equalToJson("""
                {"orderId": "%s", "customerEmail": "ada@example.com", "amount": 299.30, "currency": "EUR",
                 "paymentMethod": "pm_card_visa"}""".formatted(orderId))));
        assertThat(mockMvc.get().uri("/api/orders/{orderId}", orderId))
                .bodyJson().extractingPath("$.lines.length()").isEqualTo(2);
        assertThat(orderEventTypesFor(orderId, 1)).containsExactly("ORDER_CONFIRMED");
    }

    @Test
    void rejectsTheOrderAndReleasesWhatWasReservedWhenStockRunsShort() {
        DEPENDENCIES.stubFor(post("/api/products/" + HEADLAMP + "/reservations")
                .willReturn(problem(HttpStatus.CONFLICT, "Requested 1 of HEADLAMP-400 but only 0 available")));

        MvcTestResult rejected = placeOrder("grace@example.com", SHOE, 1, HEADLAMP, 1);

        assertThat(rejected).hasStatus(HttpStatus.CONFLICT);
        assertThat(rejected).bodyJson().extractingPath("$.detail")
                .isEqualTo("Requested 1 of HEADLAMP-400 but only 0 available");
        DEPENDENCIES.verify(deleteRequestedFor(urlMatching("/api/orders/.+/reservations")));
        assertThat(mockMvc.get().uri("/api/orders?customerEmail=grace@example.com"))
                .bodyJson().extractingPath("$.length()").isEqualTo(0);
    }

    @Test
    void aDeclinedCardFailsTheOrderAndReleasesItsStock() {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(problem(HttpStatus.PAYMENT_REQUIRED, "Your card was declined.")));

        MvcTestResult declined = placeOrder("alan@example.com", SHOE, 1);

        assertThat(declined).hasStatus(HttpStatus.PAYMENT_REQUIRED);
        assertThat(declined).bodyJson().extractingPath("$.detail").isEqualTo("Your card was declined.");
        DEPENDENCIES.verify(deleteRequestedFor(urlMatching("/api/orders/.+/reservations")));
        assertThat(mockMvc.get().uri("/api/orders?customerEmail=alan@example.com"))
                .bodyJson().extractingPath("$[0].status").isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void anUnavailablePaymentServiceFailsTheOrderAndReleasesItsStock() {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(problem(HttpStatus.SERVICE_UNAVAILABLE, "down")));

        assertThat(placeOrder("ken@example.com", SHOE, 1)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        DEPENDENCIES.verify(deleteRequestedFor(urlMatching("/api/orders/.+/reservations")));
        assertThat(mockMvc.get().uri("/api/orders?customerEmail=ken@example.com"))
                .bodyJson().extractingPath("$[0].status").isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void rejectsAnUnknownProduct() {
        assertThat(placeOrder("linus@example.com", UNKNOWN_PRODUCT, 1)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void rejectsTheSameProductOnTwoLinesWithoutCallingAnything() {
        assertThat(placeOrder("barbara@example.com", SHOE, 1, SHOE, 2)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(DEPENDENCIES.getAllServeEvents()).isEmpty();
    }

    @Test
    void reportsTheCatalogAsUnavailableWhenItCannotBeReached() {
        DEPENDENCIES.stubFor(get("/api/products/" + SHOE).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(placeOrder("edsger@example.com", SHOE, 1)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void cancellingReleasesTheStockOnceKeepsTheFirstReasonAndAnnouncesIt() throws Exception {
        String orderId = orderIdOf(placeOrder("margaret@example.com", SHOE, 1));

        MvcTestResult cancelled = cancel(orderId, "customer changed their mind");
        assertThat(cancelled).hasStatusOk();
        assertThat(cancelled).bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");

        assertThat(cancel(orderId, "a second reason"))
                .bodyJson().extractingPath("$.cancellationReason").isEqualTo("customer changed their mind");
        DEPENDENCIES.verify(1, deleteRequestedFor(urlEqualTo("/api/orders/" + orderId + "/reservations")));
        assertThat(orderEventTypesFor(orderId, 2)).containsExactly("ORDER_CONFIRMED", "ORDER_CANCELLED");
    }

    @Test
    void anOrderWhosePaymentFailedCannotBeCancelled() throws Exception {
        DEPENDENCIES.stubFor(post("/api/payments").willReturn(problem(HttpStatus.PAYMENT_REQUIRED, "Your card was declined.")));
        placeOrder("dennis@example.com", SHOE, 1);
        String orderId = JsonPath.read(mockMvc.get().uri("/api/orders?customerEmail=dennis@example.com").exchange()
                .getResponse().getContentAsString(), "$[0].id");

        assertThat(cancel(orderId, "changed mind")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsAnInvalidEmail() {
        assertThat(placeOrder("not-an-email", SHOE, 1)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private static WireMockServer startWireMock() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    private static void stubProduct(UUID productId, String sku, String name, String price) {
        DEPENDENCIES.stubFor(get("/api/products/" + productId).willReturn(okJson("""
                {"id": "%s", "sku": "%s", "name": "%s", "price": %s, "currency": "EUR", "available": 10}"""
                .formatted(productId, sku, name, price))));
    }

    private static ResponseDefinitionBuilder problem(HttpStatus status, String detail) {
        return aResponse().withStatus(status.value())
                .withHeader("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .withBody("""
                        {"status": %d, "detail": "%s"}""".formatted(status.value(), detail));
    }

    private MvcTestResult placeOrder(String customerEmail, Object... productsAndQuantities) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < productsAndQuantities.length; index += 2) {
            lines.add("""
                    {"productId": "%s", "quantity": %d}""".formatted(productsAndQuantities[index],
                    productsAndQuantities[index + 1]));
        }
        return mockMvc.post().uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"customerEmail": "%s", "lines": [%s]}""".formatted(customerEmail, String.join(", ", lines)))
                .exchange();
    }

    private MvcTestResult cancel(String orderId, String reason) {
        return mockMvc.post().uri("/api/orders/{orderId}/cancellation", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason": "%s"}""".formatted(reason))
                .exchange();
    }

    private static String orderIdOf(MvcTestResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private List<String> orderEventTypesFor(String orderId, int expectedEvents) {
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
