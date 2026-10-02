package io.github.exepex.commerce.shipping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ShipmentController {

    record ShipmentView(UUID id, UUID orderId, String customerEmail, String trackingNumber, String status,
            LocalDate estimatedDelivery, Instant createdAt, Instant shippedAt, Instant deliveredAt,
            String deliveryProblem, Instant cancelledAt) {

        static ShipmentView of(Shipment shipment) {
            return new ShipmentView(shipment.getId(), shipment.getOrderId(), shipment.getCustomerEmail(),
                    shipment.getTrackingNumber(), shipment.getStatus().name(), shipment.getEstimatedDelivery(),
                    shipment.getCreatedAt(), shipment.getShippedAt(), shipment.getDeliveredAt(),
                    shipment.getDeliveryProblem(), shipment.getCancelledAt());
        }
    }

    /**
     * {@code deliveryProblem} says why a parcel was not delivered, with a plain default when it is left out; it is not
     * used for a delivered one.
     */
    record CarrierReportRequest(@NotNull Shipment.Status outcome, @Size(max = 500) String deliveryProblem) {}

    private final ShipmentRepository shipments;
    private final Carrier carrier;

    ShipmentController(ShipmentRepository shipments, Carrier carrier) {
        this.shipments = shipments;
        this.carrier = carrier;
    }

    /** The 100 most recent shipments, optionally only those with the given statuses. */
    @GetMapping("/api/shipments")
    List<ShipmentView> findShipments(@RequestParam(required = false) List<Shipment.Status> status) {
        List<Shipment> found = status == null || status.isEmpty()
                ? shipments.findTop100ByOrderByCreatedAtDesc()
                : shipments.findTop100ByStatusInOrderByCreatedAtDesc(status);
        return found.stream().map(ShipmentView::of).toList();
    }

    @GetMapping("/api/shipments/{orderId}")
    ShipmentView getShipment(@PathVariable UUID orderId) {
        return shipments.findByOrderId(orderId).map(ShipmentView::of).orElseThrow(() -> notFound(orderId));
    }

    /** What the carrier reports about the order's shipped parcel. */
    @PostMapping("/api/shipments/{orderId}/carrier-reports")
    ShipmentView reportFromCarrier(@PathVariable UUID orderId, @Valid @RequestBody CarrierReportRequest request) {
        return ShipmentView.of(carrier.report(orderId, request.outcome(), request.deliveryProblem()));
    }

    static ErrorResponseException notFound(UUID orderId) {
        return new ErrorResponseException(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Order " + orderId + " has no shipment"), null);
    }
}
