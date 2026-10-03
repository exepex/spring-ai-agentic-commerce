package io.github.exepex.commerce.shipping;

import io.github.exepex.commerce.shipping.ShipmentViews.ShipmentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ShipmentController {

    /**
     * {@code deliveryProblem} says why a parcel was not delivered, with a plain default when it is left out; it is not
     * used for a delivered one.
     */
    record CarrierReportRequest(@NotNull Shipment.Status outcome, @Size(max = 500) String deliveryProblem) {}

    private final ShipmentRepository shipments;
    private final Carrier carrier;

    /** The 100 most recent shipments, optionally only those with the given statuses. */
    @GetMapping("/api/shipments")
    List<ShipmentView> findShipments(@RequestParam(required = false) List<Shipment.Status> status) {
        List<Shipment> found = status == null || status.isEmpty()
                ? shipments.findTop100ByOrderByCreatedAtDesc()
                : shipments.findTop100ByStatusInOrderByCreatedAtDesc(status);
        return found.stream().map(ShipmentViews::toView).toList();
    }

    @GetMapping("/api/shipments/{orderId}")
    ShipmentView getShipment(@PathVariable UUID orderId) {
        return shipments.findByOrderId(orderId).map(ShipmentViews::toView)
                .orElseThrow(() -> ShippingProblems.shipmentNotFound(orderId));
    }

    /** What the carrier reports about the order's shipped parcel. */
    @PostMapping("/api/shipments/{orderId}/carrier-reports")
    ShipmentView reportFromCarrier(@PathVariable UUID orderId, @Valid @RequestBody CarrierReportRequest request) {
        return ShipmentViews.toView(carrier.report(orderId, request.outcome(), request.deliveryProblem()));
    }
}
