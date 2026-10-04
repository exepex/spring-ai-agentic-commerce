package io.github.exepex.commerce.order;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.http.Fault;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Shipping an order from the warehouse, and the order following its parcel to the customer. */
class ShippingIntegrationTest extends OrderServiceTestSupport {

    @BeforeEach
    void stubTheDispatch() {
        DEPENDENCIES.stubFor(post(urlMatching("/api/orders/.+/dispatch")).willReturn(aResponse().withStatus(204)));
    }

    @Test
    void shipsAConfirmedOrderOnceAndAnnouncesIt() throws Exception {
        String orderId = orderIdOf(placeOrder(newCustomer(), SHOE, 1));

        assertThat(ship(orderId)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("SHIPPED");
        assertThat(ship(orderId)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("SHIPPED");

        DEPENDENCIES.verify(1, postRequestedFor(urlEqualTo("/api/orders/" + orderId + "/dispatch")));
        assertThat(orderEventTypesFor(orderId, 2)).containsExactly("ORDER_CONFIRMED", "ORDER_SHIPPED");
    }

    @Test
    void aShippedOrderCannotBeCancelled() throws Exception {
        String orderId = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        ship(orderId);

        assertThat(cancel(orderId, "customer changed their mind")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("SHIPPED");
        assertThat(status(orderId)).isEqualTo("SHIPPED");
        DEPENDENCIES.verify(0, deleteRequestedFor(urlEqualTo("/api/orders/" + orderId + "/reservations")));
    }

    @Test
    void aCancelledOrderCannotShip() throws Exception {
        String orderId = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        cancel(orderId, "customer changed their mind");

        assertThat(ship(orderId)).hasStatus(HttpStatus.CONFLICT);
        DEPENDENCIES.verify(0, postRequestedFor(urlEqualTo("/api/orders/" + orderId + "/dispatch")));
    }

    @Test
    void aCancellationDuringTheDispatchWinsAndTheShipmentIsRefused() throws Exception {
        String orderId = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        DEPENDENCIES.stubFor(post("/api/orders/" + orderId + "/dispatch")
                .willReturn(aResponse().withStatus(204).withFixedDelay(1500)));

        CompletableFuture<MvcTestResult> shipping = CompletableFuture.supplyAsync(() -> ship(orderId));
        await().atMost(Duration.ofSeconds(5)).until(() -> !DEPENDENCIES.findAll(
                postRequestedFor(urlEqualTo("/api/orders/" + orderId + "/dispatch"))).isEmpty());
        assertThat(cancel(orderId, "customer changed their mind")).hasStatusOk();

        assertThat(shipping.get()).hasStatus(HttpStatus.CONFLICT);
        assertThat(status(orderId)).isEqualTo("CANCELLED");
        // The catalog puts the dispatched units back when it is asked to release the cancelled order's stock.
        DEPENDENCIES.verify(1, deleteRequestedFor(urlEqualTo("/api/orders/" + orderId + "/reservations")));
        assertThat(orderEventTypesFor(orderId, 2)).containsExactly("ORDER_CONFIRMED", "ORDER_CANCELLED");
    }

    @Test
    void refusesToShipWhenTheWarehouseNoLongerHasTheStockAndTheOrderCanStillBeCancelled() throws Exception {
        String orderId = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        DEPENDENCIES.stubFor(post("/api/orders/" + orderId + "/dispatch").willReturn(problem(HttpStatus.CONFLICT,
                "The stock on hand of RUN-SHOE-BLUE-42 no longer covers order " + orderId)));

        assertThat(ship(orderId)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("no longer covers");
        assertThat(status(orderId)).isEqualTo("CONFIRMED");
        assertThat(cancel(orderId, "out of stock")).hasStatusOk();
    }

    @Test
    void reportsTheCatalogAsUnavailableAndLeavesTheOrderConfirmed() throws Exception {
        String orderId = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        DEPENDENCIES.stubFor(post("/api/orders/" + orderId + "/dispatch")
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThat(ship(orderId)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(status(orderId)).isEqualTo("CONFIRMED");
    }

    @Test
    void theOrderFollowsWhatTheCarrierReportsEvenWhenAReportArrivesTwice() throws Exception {
        String delivered = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        String failed = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        String lost = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        String notShipped = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        ship(delivered);
        ship(failed);
        ship(lost);

        publishCarrierReport(delivered, "SHIPMENT_DELIVERED");
        publishCarrierReport(delivered, "SHIPMENT_DELIVERED");
        publishCarrierReport(failed, "SHIPMENT_DELIVERY_FAILED");
        publishCarrierReport(lost, "SHIPMENT_LOST");
        publishCarrierReport(notShipped, "SHIPMENT_DELIVERED");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(status(delivered)).isEqualTo("DELIVERED");
            assertThat(status(failed)).isEqualTo("DELIVERY_FAILED");
            assertThat(status(lost)).isEqualTo("LOST");
        });
        assertThat(status(notShipped)).isEqualTo("CONFIRMED");
    }

    /** A customer of its own, so these orders never show up in another test's list of a customer's orders. */
    private static String newCustomer() {
        return "shipping-" + UUID.randomUUID() + "@example.com";
    }

    private MvcTestResult ship(String orderId) {
        return mockMvc.post().uri("/api/orders/{orderId}/dispatch", orderId).exchange();
    }

    @Test
    void anUnreadableCarrierReportIsParkedAndTheReportsBehindItStillArrive() throws Exception {
        String orderId = orderIdOf(placeOrder(newCustomer(), SHOE, 1));
        ship(orderId);

        publish(orderId, "the carrier's system sent this by mistake");
        publishCarrierReport(orderId, "SHIPMENT_DELIVERED");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(status(orderId)).isEqualTo("DELIVERED"));
        assertThat(deadLettersFor(orderId)).containsExactly("the carrier's system sent this by mistake");
    }

    private String status(String orderId) throws Exception {
        return JsonPath.read(mockMvc.get().uri("/api/orders/{orderId}", orderId).exchange()
                .getResponse().getContentAsString(), "$.status");
    }

    private void publishCarrierReport(String orderId, String type) {
        publish(orderId, """
                {"eventId": "%s", "type": "%s", "orderId": "%s", "occurredAt": "2026-10-02T10:00:00Z"}"""
                .formatted(UUID.randomUUID(), type, orderId));
    }

    private void publish(String orderId, String value) {
        Map<String, Object> producerProperties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
            producer.send(new ProducerRecord<>("shipment.events", orderId, value));
        }
    }

    private List<String> deadLettersFor(String orderId) {
        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dead-letters-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of("shipment.events.DLT"));
            List<String> parked = new ArrayList<>();
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (parked.isEmpty() && System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> event : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2))) {
                    if (orderId.equals(event.key())) {
                        parked.add(event.value());
                    }
                }
            }
            return parked;
        }
    }
}
