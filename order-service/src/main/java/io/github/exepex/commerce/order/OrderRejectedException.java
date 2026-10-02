package io.github.exepex.commerce.order;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The order cannot be placed as requested; nothing was reserved. */
class OrderRejectedException extends ErrorResponseException {

    private OrderRejectedException(HttpStatus status, String detail) {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }

    static OrderRejectedException unknownProduct(UUID productId) {
        return new OrderRejectedException(HttpStatus.UNPROCESSABLE_CONTENT, "Product " + productId + " does not exist");
    }

    static OrderRejectedException stockUnavailable(String catalogDetail) {
        return new OrderRejectedException(HttpStatus.CONFLICT, catalogDetail);
    }

    static OrderRejectedException duplicateProduct(UUID productId) {
        return new OrderRejectedException(HttpStatus.UNPROCESSABLE_CONTENT,
                "Product " + productId + " appears on more than one line; combine them into one");
    }

    static OrderRejectedException mixedCurrencies() {
        return new OrderRejectedException(HttpStatus.UNPROCESSABLE_CONTENT, "All products in an order must share a currency");
    }
}
