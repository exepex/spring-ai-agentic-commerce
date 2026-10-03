package io.github.exepex.commerce.shipping;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The shipping-service's error responses, as RFC 9457 problem details. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ShippingProblems {

    static ErrorResponseException shipmentNotFound(UUID orderId) {
        return problem(HttpStatus.NOT_FOUND, "Order " + orderId + " has no shipment");
    }

    static ErrorResponseException notACarrierOutcome(Shipment.Status outcome) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT,
                "The carrier reports DELIVERED, DELIVERY_FAILED or LOST, not " + outcome);
    }

    static ErrorResponseException notOnItsWay(UUID orderId, Shipment.Status status) {
        return problem(HttpStatus.CONFLICT, "The shipment of order " + orderId + " is " + status
                + "; the carrier only reports on a parcel that has shipped and is still on its way");
    }

    private static ErrorResponseException problem(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
