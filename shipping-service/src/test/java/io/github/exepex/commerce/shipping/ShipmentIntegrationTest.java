package io.github.exepex.commerce.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShipmentIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private KafkaContainer kafkaContainer;

    @Test
    void createsOneShipmentPerConfirmedOrderEvenWhenTheEventArrivesTwice() {
        UUID orderId = UUID.randomUUID();

        publish(orderId, "ORDER_CONFIRMED");
        publish(orderId, "ORDER_CONFIRMED");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(mockMvc.get().uri("/api/shipments/{orderId}", orderId))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("PREPARING"));
        assertThat(shipments.findAll()).filteredOn(shipment -> shipment.getOrderId().equals(orderId)).hasSize(1);
    }

    @Test
    void cancelsTheShipmentWhenTheOrderIsCancelled() {
        UUID orderId = UUID.randomUUID();

        publish(orderId, "ORDER_CONFIRMED");
        publish(orderId, "ORDER_CANCELLED");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(mockMvc.get().uri("/api/shipments/{orderId}", orderId))
                .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED"));
    }

    @Test
    void handsTheParcelToTheCarrierWhenTheOrderShipsEvenIfTheConfirmationWasMissed() {
        UUID orderId = UUID.randomUUID();

        publish(orderId, "ORDER_SHIPPED");
        publish(orderId, "ORDER_SHIPPED");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(shipment(orderId))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("SHIPPED"));
        // The time the order shipped, which the event carries, not the time the listener read it.
        assertThat(shipment(orderId)).bodyJson().extractingPath("$.shippedAt").isEqualTo("2026-10-02T10:00:00Z");
        assertThat(shipments.findAll()).filteredOn(shipment -> shipment.getOrderId().equals(orderId)).hasSize(1);
    }

    @Test
    void theCarrierDeliversAShippedParcelOnceAndAnnouncesIt() {
        UUID orderId = shippedOrder();

        assertThat(report(orderId, "DELIVERED", null)).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("DELIVERED");
        assertThat(report(orderId, "DELIVERED", null)).hasStatusOk();
        assertThat(report(orderId, "LOST", null)).hasStatus(HttpStatus.CONFLICT);

        assertThat(shipment(orderId)).bodyJson().extractingPath("$.deliveredAt").isNotNull();
        List<String> events = shipmentEventsFor(orderId, 1);
        assertThat(events).hasSize(1);
        assertThat((String) JsonPath.read(events.getFirst(), "$.type")).isEqualTo("SHIPMENT_DELIVERED");
    }

    @Test
    void aFailedDeliveryOrALostParcelIsAnnouncedWithWhatWentWrong() {
        UUID failed = shippedOrder();
        UUID lost = shippedOrder();

        assertThat(report(failed, "DELIVERY_FAILED", "Nobody home, parcel returned")).hasStatusOk()
                .bodyJson().extractingPath("$.deliveryProblem").isEqualTo("Nobody home, parcel returned");
        assertThat(report(lost, "LOST", " ")).hasStatusOk()
                .bodyJson().extractingPath("$.deliveryProblem").isEqualTo("The carrier lost the parcel");

        String failedEvent = shipmentEventsFor(failed, 1).getFirst();
        assertThat((String) JsonPath.read(failedEvent, "$.type")).isEqualTo("SHIPMENT_DELIVERY_FAILED");
        assertThat((String) JsonPath.read(failedEvent, "$.deliveryProblem")).isEqualTo("Nobody home, parcel returned");
        assertThat((String) JsonPath.read(shipmentEventsFor(lost, 1).getFirst(), "$.type")).isEqualTo("SHIPMENT_LOST");
    }

    @Test
    void theCarrierOnlyReportsOnAParcelThatHasShipped() {
        UUID preparing = UUID.randomUUID();
        publish(preparing, "ORDER_CONFIRMED");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(shipment(preparing)).hasStatusOk());

        assertThat(report(preparing, "DELIVERED", null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(report(UUID.randomUUID(), "DELIVERED", null)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(report(shippedOrder(), "PREPARING", null)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void twoDifferentReportsAtOnceAreTakenOneAfterTheOther() throws Exception {
        UUID orderId = shippedOrder();

        List<Callable<Integer>> reports = List.of(
                () -> report(orderId, "DELIVERED", null).getResponse().getStatus(),
                () -> report(orderId, "LOST", null).getResponse().getStatus());
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService threads = Executors.newFixedThreadPool(reports.size())) {
            for (Future<Integer> status : threads.invokeAll(reports)) {
                statuses.add(status.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK.value(), HttpStatus.CONFLICT.value());
        assertThat(shipmentEventsFor(orderId, 1)).hasSize(1);
    }

    @Test
    void listsShipmentsByStatus() {
        UUID orderId = shippedOrder();

        assertThat(mockMvc.get().uri("/api/shipments?status=SHIPPED&status=PREPARING"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[?(@.orderId == '%s')].status".formatted(orderId)).asArray()
                .containsExactly("SHIPPED");
        assertThat(mockMvc.get().uri("/api/shipments?status=DELIVERED"))
                .bodyJson().extractingPath("$[?(@.orderId == '%s')]".formatted(orderId)).asArray().isEmpty();
    }

    private UUID shippedOrder() {
        UUID orderId = UUID.randomUUID();
        publish(orderId, "ORDER_CONFIRMED");
        publish(orderId, "ORDER_SHIPPED");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(shipment(orderId))
                .bodyJson().extractingPath("$.status").isEqualTo("SHIPPED"));
        return orderId;
    }

    private MvcTestResult shipment(UUID orderId) {
        return mockMvc.get().uri("/api/shipments/{orderId}", orderId).exchange();
    }

    private MvcTestResult report(UUID orderId, String outcome, String deliveryProblem) {
        return mockMvc.post().uri("/api/shipments/{orderId}/carrier-reports", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"outcome": "%s", "deliveryProblem": %s}""".formatted(outcome,
                        deliveryProblem == null ? "null" : "\"" + deliveryProblem + "\""))
                .exchange();
    }

    /** The events published for one order; waits for {@code expectedEvents}, then a moment longer for any extra. */
    private List<String> shipmentEventsFor(UUID orderId, int expectedEvents) {
        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "shipping-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of("shipment.events"));
            List<String> events = new ArrayList<>();
            Instant deadline = Instant.now().plusSeconds(20);
            Instant settled = null;
            while (Instant.now().isBefore(deadline) && (settled == null || Instant.now().isBefore(settled))) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (orderId.toString().equals(record.key())) {
                        events.add(record.value());
                    }
                }
                if (settled == null && events.size() >= expectedEvents) {
                    settled = Instant.now().plusSeconds(2);
                }
            }
            return events;
        }
    }

    /** Publishes as the order service does: plain JSON, keyed by order id. */
    private void publish(UUID orderId, String type) {
        Map<String, Object> producerProperties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
            producer.send(new ProducerRecord<>("order.events", orderId.toString(), """
                    {"eventId": "%s", "type": "%s", "orderId": "%s", "customerEmail": "ada@example.com",
                     "occurredAt": "2026-10-02T10:00:00Z"}""".formatted(UUID.randomUUID(), type, orderId)));
        }
    }
}
