package io.github.exepex.commerce.shipping;

import io.github.exepex.commerce.shipping.constants.ApiPaths;
import io.github.exepex.commerce.shipping.dto.CarrierReportRequest;
import io.github.exepex.commerce.shipping.dto.ShipmentView;
import io.github.exepex.commerce.shipping.exception.ShipmentNotFoundException;
import jakarta.validation.Valid;
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

    private final ShipmentRepository shipments;
    private final Carrier carrier;

    /** The 100 most recent shipments, optionally only those with the given statuses. */
    @GetMapping(ApiPaths.SHIPMENTS)
    List<ShipmentView> findShipments(@RequestParam(required = false) List<Shipment.Status> status) {
        var found = status == null || status.isEmpty()
                ? shipments.findTop100ByOrderByCreatedAtDesc()
                : shipments.findTop100ByStatusInOrderByCreatedAtDesc(status);
        return found.stream().map(ShipmentMapper::toView).toList();
    }

    @GetMapping(ApiPaths.SHIPMENT)
    ShipmentView getShipment(@PathVariable UUID orderId) {
        return shipments.findByOrderId(orderId).map(ShipmentMapper::toView)
                .orElseThrow(() -> new ShipmentNotFoundException(orderId));
    }

    /** What the carrier reports about the order's shipped parcel. */
    @PostMapping(ApiPaths.CARRIER_REPORTS)
    ShipmentView reportFromCarrier(@PathVariable UUID orderId, @Valid @RequestBody CarrierReportRequest request) {
        return ShipmentMapper.toView(carrier.report(orderId, request.outcome(), request.deliveryProblem()));
    }
}
