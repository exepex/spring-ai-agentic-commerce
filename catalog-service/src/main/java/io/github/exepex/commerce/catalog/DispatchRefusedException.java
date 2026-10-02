package io.github.exepex.commerce.catalog;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The order's stock cannot leave the warehouse, so the order cannot ship. */
class DispatchRefusedException extends ErrorResponseException {

    private DispatchRefusedException(String detail) {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail), null);
    }

    static DispatchRefusedException nothingReserved(UUID orderId) {
        return new DispatchRefusedException("Order " + orderId + " has no stock reserved");
    }

    static DispatchRefusedException released(UUID orderId) {
        return new DispatchRefusedException("The stock of order " + orderId + " was released; the order cannot ship");
    }

    static DispatchRefusedException stockShort(UUID orderId, String sku) {
        return new DispatchRefusedException("The stock on hand of " + sku + " no longer covers order " + orderId
                + " after a stock-out; the order cannot ship");
    }
}
