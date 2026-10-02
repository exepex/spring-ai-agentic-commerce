package io.github.exepex.commerce.shipping;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ShipmentController {

    record ShipmentView(UUID id, UUID orderId, String trackingNumber, String status, LocalDate estimatedDelivery,
            Instant createdAt, Instant cancelledAt) {}

    private final ShipmentRepository shipments;

    ShipmentController(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @GetMapping("/api/shipments/{orderId}")
    ShipmentView getShipment(@PathVariable UUID orderId) {
        return shipments.findByOrderId(orderId)
                .map(shipment -> new ShipmentView(shipment.getId(), shipment.getOrderId(), shipment.getTrackingNumber(),
                        shipment.getStatus().name(), shipment.getEstimatedDelivery(), shipment.getCreatedAt(),
                        shipment.getCancelledAt()))
                .orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND,
                        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Order " + orderId + " has no shipment"),
                        null));
    }
}
