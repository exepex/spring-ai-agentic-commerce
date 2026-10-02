package io.github.exepex.commerce.shipping;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a shipment when an order is confirmed and cancels it when the order is cancelled. Kafka may deliver an
 * event more than once, so both steps are safe to repeat.
 */
@Component
class OrderEventListener {

    private static final int DELIVERY_DAYS = 3;
    private static final String TRACKING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ShipmentRepository shipments;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    OrderEventListener(ShipmentRepository shipments, Clock clock) {
        this.shipments = shipments;
        this.clock = clock;
    }

    @KafkaListener(topics = "${commerce.topics.order-events}")
    @Transactional
    void onOrderEvent(OrderEvent event) {
        switch (event.type()) {
            case ORDER_CONFIRMED -> createShipment(event);
            case ORDER_CANCELLED -> shipments.findByOrderId(event.orderId()).ifPresent(shipment -> shipment.cancel(Instant.now(clock)));
        }
    }

    private void createShipment(OrderEvent event) {
        if (shipments.findByOrderId(event.orderId()).isPresent()) {
            return;
        }
        LocalDate estimatedDelivery = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC).plusDays(DELIVERY_DAYS);
        shipments.save(new Shipment(event.orderId(), event.customerEmail(), newTrackingNumber(), estimatedDelivery,
                Instant.now(clock)));
    }

    private String newTrackingNumber() {
        StringBuilder trackingNumber = new StringBuilder("AC");
        for (int position = 0; position < 10; position++) {
            trackingNumber.append(TRACKING_ALPHABET.charAt(random.nextInt(TRACKING_ALPHABET.length())));
        }
        return trackingNumber.toString();
    }
}
