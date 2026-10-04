package io.github.exepex.commerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;

/** Each test works on its own seeded product, so the tests share one database without affecting each other. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogApiIntegrationTest {

    private static final UUID SHOE_42 = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0001");
    private static final UUID SHOE_43 = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0002");
    private static final UUID RAIN_JACKET = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0003");
    private static final UUID HEADLAMP = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0004");
    private static final UUID BOTTLE = UUID.fromString("8c1f8a52-6f53-4f37-9d2e-1b0a9a6c0005");

    /** What the services that call this one present; anyone else is refused. */
    private static final String SERVICE_TOKEN = "Bearer dev-internal-api-token";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private KafkaContainer kafka;

    @Test
    void listsTheSeededProducts() {
        assertThat(mockMvc.get().uri("/api/products"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(5);
    }

    @Test
    void rejectsAReservationLargerThanTheAvailableStock() {
        assertThat(reserve(BOTTLE, UUID.randomUUID(), 2)).hasStatus(HttpStatus.CONFLICT);

        assertThat(reserve(BOTTLE, UUID.randomUUID(), 1)).hasStatus(HttpStatus.CREATED);
        assertThat(product(BOTTLE)).bodyJson().extractingPath("$.available").isEqualTo(0);
    }

    @Test
    void repeatingAReservationReturnsTheSameOneAndAChangedQuantityIsRejected() throws Exception {
        UUID orderId = UUID.randomUUID();
        String firstReservationId = JsonPath.read(reserve(HEADLAMP, orderId, 2).getResponse().getContentAsString(), "$.id");

        assertThat(reserve(HEADLAMP, orderId, 2))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.id").isEqualTo(firstReservationId);
        assertThat(reserve(HEADLAMP, orderId, 3)).hasStatus(HttpStatus.CONFLICT);
        assertThat(product(HEADLAMP)).bodyJson().extractingPath("$.reserved").isEqualTo(2);
    }

    @Test
    void releasingAnOrderReturnsItsUnitsAndReleasingTwiceChangesNothing() {
        UUID orderId = UUID.randomUUID();
        reserve(RAIN_JACKET, orderId, 5);

        assertThat(mockMvc.delete().uri("/api/orders/{orderId}/reservations", orderId)
                .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mockMvc.delete().uri("/api/orders/{orderId}/reservations", orderId)
                .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(product(RAIN_JACKET)).bodyJson().extractingPath("$.reserved").isEqualTo(0);
    }

    @Test
    void releasingTheSameOrderManyTimesAtOnceGivesItsUnitsBackOnce() throws Exception {
        UUID released = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        reserve(RAIN_JACKET, released, 5);
        reserve(RAIN_JACKET, kept, 3);

        List<Callable<Integer>> releases = new ArrayList<>();
        for (int attempt = 0; attempt < 8; attempt++) {
            releases.add(() -> mockMvc.delete().uri("/api/orders/{orderId}/reservations", released)
                    .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN).exchange()
                    .getResponse().getStatus());
        }
        try (ExecutorService threads = Executors.newFixedThreadPool(releases.size())) {
            for (Future<Integer> status : threads.invokeAll(releases)) {
                assertThat(status.get()).isEqualTo(HttpStatus.NO_CONTENT.value());
            }
        }

        assertThat(product(RAIN_JACKET)).bodyJson().extractingPath("$.reserved").isEqualTo(3);
        mockMvc.delete().uri("/api/orders/{orderId}/reservations", kept)
                .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN).exchange();
    }

    @Test
    void rejectsAWriteOffThatWouldTakeStockBelowZero() {
        assertThat(adjust(SHOE_42, -100, "miscount")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void aWriteOffBelowTheReservedUnitsPublishesAStockOutNamingTheNewestOrders() {
        UUID oldestOrder = UUID.randomUUID();
        UUID middleOrder = UUID.randomUUID();
        UUID newestOrder = UUID.randomUUID();
        reserve(SHOE_43, oldestOrder, 1);
        reserve(SHOE_43, middleOrder, 1);
        reserve(SHOE_43, newestOrder, 1);

        assertThat(adjust(SHOE_43, -2, "damaged in warehouse"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.onHand").isEqualTo(1);

        String event = readStockOutEventFor("RUN-SHOE-BLUE-43");
        assertThat((String) JsonPath.read(event, "$.sku")).isEqualTo("RUN-SHOE-BLUE-43");
        assertThat((Integer) JsonPath.read(event, "$.shortfall")).isEqualTo(2);
        assertThat((String) JsonPath.read(event, "$.reason")).isEqualTo("damaged in warehouse");
        assertThat((List<String>) JsonPath.read(event, "$.affectedOrderIds"))
                .containsExactly(newestOrder.toString(), middleOrder.toString());
    }

    @Test
    void dispatchingAnOrderTakesItsUnitsOffTheShelfOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        reserve(HEADLAMP, orderId, 3);
        int onHandBefore = JsonPath.read(product(HEADLAMP).getResponse().getContentAsString(), "$.onHand");
        int reservedBefore = JsonPath.read(product(HEADLAMP).getResponse().getContentAsString(), "$.reserved");

        assertThat(dispatch(orderId)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(dispatch(orderId)).hasStatus(HttpStatus.NO_CONTENT);

        String product = product(HEADLAMP).getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(product, "$.onHand")).isEqualTo(onHandBefore - 3);
        assertThat((Integer) JsonPath.read(product, "$.reserved")).isEqualTo(reservedBefore - 3);
    }

    @Test
    void releasingAnOrderCancelledWhileItWasDispatchedPutsItsUnitsBackAndItCannotShipAnyMore() throws Exception {
        UUID orderId = UUID.randomUUID();
        int onHandBefore = JsonPath.read(product(RAIN_JACKET).getResponse().getContentAsString(), "$.onHand");
        reserve(RAIN_JACKET, orderId, 2);
        dispatch(orderId);

        assertThat(mockMvc.delete().uri("/api/orders/{orderId}/reservations", orderId)
                .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mockMvc.delete().uri("/api/orders/{orderId}/reservations", orderId)
                .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(product(RAIN_JACKET)).bodyJson().extractingPath("$.onHand").isEqualTo(onHandBefore);
        assertThat(dispatch(orderId)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void refusesToDispatchAnOrderAStockOutLeftUncoveredButShipsTheOlderOne() {
        UUID olderOrder = UUID.randomUUID();
        UUID newerOrder = UUID.randomUUID();
        reserve(SHOE_42, olderOrder, 1);
        reserve(SHOE_42, newerOrder, 1);
        adjust(SHOE_42, -11, "water damage");

        assertThat(dispatch(newerOrder)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("RUN-SHOE-BLUE-42");
        assertThat(dispatch(olderOrder)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(product(SHOE_42)).bodyJson().extractingPath("$.reserved").isEqualTo(1);
    }

    @Test
    void onlyAServiceWithTheTokenCanReserveReleaseOrHandOverStock() {
        var orderId = UUID.randomUUID();

        assertThat(mockMvc.post().uri("/api/products/{productId}/reservations", HEADLAMP)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId": "%s", "quantity": 1}""".formatted(orderId)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mockMvc.delete().uri("/api/orders/{orderId}/reservations", orderId))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mockMvc.post().uri("/api/orders/{orderId}/dispatch", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer guessed"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mockMvc.get().uri("/api/products")).hasStatusOk();
    }

    @Test
    void refusesToDispatchAnOrderWithNoStockReserved() {
        assertThat(dispatch(UUID.randomUUID())).hasStatus(HttpStatus.CONFLICT);
    }

    private MvcTestResult reserve(UUID productId, UUID orderId, int quantity) {
        return mockMvc.post().uri("/api/products/{productId}/reservations", productId)
                .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId": "%s", "quantity": %d}""".formatted(orderId, quantity))
                .exchange();
    }

    private MvcTestResult adjust(UUID productId, int delta, String reason) {
        return mockMvc.post().uri("/api/products/{productId}/stock-adjustments", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"delta": %d, "reason": "%s"}""".formatted(delta, reason))
                .exchange();
    }

    private MvcTestResult dispatch(UUID orderId) {
        return mockMvc.post().uri("/api/orders/{orderId}/dispatch", orderId)
                .header(HttpHeaders.AUTHORIZATION, SERVICE_TOKEN).exchange();
    }

    private MvcTestResult product(UUID productId) {
        return mockMvc.get().uri("/api/products/{productId}", productId).exchange();
    }

    /** The stock-out of one product: other tests publish stock-outs of their own products to the same topic. */
    private String readStockOutEventFor(String sku) {
        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "catalog-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of("inventory.stock-out"));
            Instant deadline = Instant.now().plusSeconds(20);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (sku.equals(JsonPath.read(record.value(), "$.sku"))) {
                        return record.value();
                    }
                }
            }
        }
        throw new AssertionError("No stock-out of " + sku + " was published");
    }
}
