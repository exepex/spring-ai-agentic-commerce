package io.github.exepex.commerce.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShipmentIntegrationTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ShipmentRepository shipments;

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

    private void publish(UUID orderId, String type) {
        kafka.send("order.events", orderId.toString(), """
                {"eventId": "%s", "type": "%s", "orderId": "%s", "customerEmail": "ada@example.com",
                 "occurredAt": "2026-10-02T10:00:00Z"}""".formatted(UUID.randomUUID(), type, orderId)).join();
    }
}
