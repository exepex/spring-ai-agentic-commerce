package io.github.exepex.commerce.catalog;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class ReservationConflictException extends ErrorResponseException {

    ReservationConflictException(UUID orderId, String sku, StockReservation existing) {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "Order " + orderId + " already has a " + existing.getStatus() + " reservation of "
                                + existing.getQuantity() + " " + sku),
                null);
    }
}
