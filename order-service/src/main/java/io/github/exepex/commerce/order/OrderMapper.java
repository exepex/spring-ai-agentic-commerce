package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.OrderValues;
import io.github.exepex.commerce.order.dto.LineView;
import io.github.exepex.commerce.order.dto.OrderView;
import io.github.exepex.commerce.order.dto.PlaceOrderRequest;
import io.github.exepex.commerce.order.dto.RequestedLine;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How order requests turn into what checkout takes, and how orders and their lines are shown through the order API. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OrderMapper {

    /** The order's payment method, or the test Visa card when the request names none. */
    static String paymentMethodOf(PlaceOrderRequest request) {
        var paymentMethod = request.paymentMethod();
        return paymentMethod == null || paymentMethod.isBlank() ? OrderValues.DEFAULT_PAYMENT_METHOD : paymentMethod;
    }

    static List<RequestedLine> requestedLinesOf(PlaceOrderRequest request) {
        return request.lines().stream().map(line -> new RequestedLine(line.productId(), line.quantity())).toList();
    }

    static LineView toView(OrderLine line) {
        return new LineView(line.getProductId(), line.getSku(), line.getProductName(), line.getQuantity(),
                line.getUnitPrice(), line.lineTotal());
    }

    static OrderView toView(CustomerOrder order) {
        return new OrderView(order.getId(), order.getCustomerEmail(), order.getStatus(), order.getTotalAmount(),
                order.getCurrency(), order.getCreatedAt(), order.getCancelledAt(), order.getCancellationReason(),
                order.getPaymentFailure(), order.getLines().stream().map(OrderMapper::toView).toList());
    }
}
