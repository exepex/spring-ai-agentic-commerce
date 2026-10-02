package io.github.exepex.commerce.order;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The order cannot be placed or changed as requested; any stock reserved for it was released. */
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

    static OrderRejectedException paymentDeclined(String reason) {
        return new OrderRejectedException(HttpStatus.PAYMENT_REQUIRED, reason);
    }

    static OrderRejectedException notCancellable(UUID orderId, OrderStatus status) {
        return new OrderRejectedException(HttpStatus.CONFLICT, "Order " + orderId + " is " + status + " and cannot be cancelled");
    }

    static OrderRejectedException idTaken(UUID orderId) {
        return new OrderRejectedException(HttpStatus.CONFLICT, "Order " + orderId + " already exists for another customer");
    }

    static OrderRejectedException mixedCurrencies() {
        return new OrderRejectedException(HttpStatus.UNPROCESSABLE_CONTENT, "All products in an order must share a currency");
    }
}
