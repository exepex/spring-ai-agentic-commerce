package io.github.exepex.commerce.shipping;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The simulated carrier. In the demo a person plays it from the operations console: they report a shipped parcel as
 * delivered, not delivered or lost, and the report is announced on {@code shipment.events}.
 */
@Service
@RequiredArgsConstructor
class Carrier {

    private final ShipmentRepository shipments;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Records the carrier's report on the order's parcel. Reporting the same outcome again changes nothing; a
     * different outcome for a parcel the carrier already reported on, or a report on a parcel that has not shipped,
     * is refused.
     */
    @Transactional
    public Shipment report(UUID orderId, Shipment.Status outcome, String deliveryProblem) {
        if (!outcome.isCarrierOutcome()) {
            throw ShippingProblems.notACarrierOutcome(outcome);
        }
        Shipment shipment = shipments.findForUpdate(orderId)
                .orElseThrow(() -> ShippingProblems.shipmentNotFound(orderId));
        if (shipment.getStatus() == outcome) {
            return shipment;
        }
        if (shipment.getStatus() != Shipment.Status.SHIPPED) {
            throw ShippingProblems.notOnItsWay(orderId, shipment.getStatus());
        }
        Instant now = Instant.now(clock);
        shipment.recordCarrierOutcome(outcome, problemOrDefault(outcome, deliveryProblem), now);
        events.publishEvent(ShipmentEvent.of(shipment, now));
        return shipment;
    }

    /** A report that leaves out what went wrong gets a plain description of the outcome. */
    private static String problemOrDefault(Shipment.Status outcome, String deliveryProblem) {
        if (deliveryProblem != null && !deliveryProblem.isBlank()) {
            return deliveryProblem.strip();
        }
        return outcome == Shipment.Status.LOST ? "The carrier lost the parcel" : "The carrier could not deliver the parcel";
    }
}
