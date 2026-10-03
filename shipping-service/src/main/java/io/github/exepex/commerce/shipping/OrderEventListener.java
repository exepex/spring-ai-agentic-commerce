package io.github.exepex.commerce.shipping;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a shipment when an order is confirmed, cancels it when the order is cancelled, and hands it to the carrier
 * when the order ships. Kafka may deliver an event more than once, so every step is safe to repeat.
 */
@Component
@RequiredArgsConstructor
class OrderEventListener {

    private static final int DELIVERY_DAYS = 3;

    private final ShipmentRepository shipments;
    private final Clock clock;

    @KafkaListener(topics = "${commerce.topics.order-events}")
    @Transactional
    void onOrderEvent(OrderEvent event) {
        switch (event.type()) {
            case ORDER_CONFIRMED -> shipmentFor(event);
            case ORDER_CANCELLED -> shipments.findByOrderId(event.orderId())
                    .ifPresent(shipment -> shipment.cancel(Instant.now(clock)));
            // When the order shipped, not when this listener caught up with the event.
            case ORDER_SHIPPED -> shipmentFor(event).ship(event.occurredAt());
        }
    }

    /** The order's shipment, created if it does not exist yet. */
    private Shipment shipmentFor(OrderEvent event) {
        return shipments.findByOrderId(event.orderId()).orElseGet(() -> {
            LocalDate estimatedDelivery = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC).plusDays(DELIVERY_DAYS);
            return shipments.save(new Shipment(event.orderId(), event.customerEmail(), TrackingNumbers.next(),
                    estimatedDelivery, Instant.now(clock)));
        });
    }
}
