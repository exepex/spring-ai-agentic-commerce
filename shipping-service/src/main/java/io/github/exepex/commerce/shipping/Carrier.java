package io.github.exepex.commerce.shipping;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

/**
 * The simulated carrier. In the demo a person plays it from the operations console: they report a shipped parcel as
 * delivered, not delivered or lost, and the report is announced on {@code shipment.events}.
 */
@Service
class Carrier {

    private final ShipmentRepository shipments;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    Carrier(ShipmentRepository shipments, ApplicationEventPublisher events, Clock clock) {
        this.shipments = shipments;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Records the carrier's report on the order's parcel. Reporting the same outcome again changes nothing; a
     * different outcome for a parcel the carrier already reported on, or a report on a parcel that has not shipped,
     * is refused.
     */
    @Transactional
    public Shipment report(UUID orderId, Shipment.Status outcome, String deliveryProblem) {
        if (!outcome.isCarrierOutcome()) {
            throw refused(HttpStatus.UNPROCESSABLE_CONTENT,
                    "The carrier reports DELIVERED, DELIVERY_FAILED or LOST, not " + outcome);
        }
        Shipment shipment = shipments.findForUpdate(orderId).orElseThrow(() -> ShipmentController.notFound(orderId));
        if (shipment.getStatus() == outcome) {
            return shipment;
        }
        if (shipment.getStatus() != Shipment.Status.SHIPPED) {
            throw refused(HttpStatus.CONFLICT, "The shipment of order " + orderId + " is " + shipment.getStatus()
                    + "; the carrier only reports on a parcel that has shipped and is still on its way");
        }
        Instant now = Instant.now(clock);
        shipment.recordCarrierOutcome(outcome, deliveryProblem == null || deliveryProblem.isBlank()
                ? defaultProblem(outcome)
                : deliveryProblem.strip(), now);
        events.publishEvent(ShipmentEvent.of(shipment, now));
        return shipment;
    }

    private static String defaultProblem(Shipment.Status outcome) {
        return outcome == Shipment.Status.LOST ? "The carrier lost the parcel" : "The carrier could not deliver the parcel";
    }

    private static ErrorResponseException refused(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
