package io.github.exepex.commerce.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the order API answers with, and how orders and their lines turn into it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OrderViews {

    record LineView(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice,
            BigDecimal lineTotal) {}

    record OrderView(UUID id, String customerEmail, OrderStatus status, BigDecimal total, String currency,
            Instant createdAt, Instant cancelledAt, String cancellationReason, String paymentFailure,
            List<LineView> lines) {}

    static LineView toView(OrderLine line) {
        return new LineView(line.getProductId(), line.getSku(), line.getProductName(), line.getQuantity(),
                line.getUnitPrice(), line.lineTotal());
    }

    static OrderView toView(CustomerOrder order) {
        return new OrderView(order.getId(), order.getCustomerEmail(), order.getStatus(), order.getTotalAmount(),
                order.getCurrency(), order.getCreatedAt(), order.getCancelledAt(), order.getCancellationReason(),
                order.getPaymentFailure(), order.getLines().stream().map(OrderViews::toView).toList());
    }
}
