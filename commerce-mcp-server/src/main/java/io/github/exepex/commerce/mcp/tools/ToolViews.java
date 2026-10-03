package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.downstream.CatalogApi;
import io.github.exepex.commerce.mcp.downstream.OrderApi;
import io.github.exepex.commerce.mcp.downstream.PaymentApi;
import io.github.exepex.commerce.mcp.downstream.ShippingApi;
import io.github.exepex.commerce.mcp.governance.CustomerNotification;
import io.github.exepex.commerce.mcp.governance.RefundRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the shop's tools answer an agent with, and how the services' answers and the shop's records turn into it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ToolViews {

    record ProductSummary(UUID productId, String sku, String name, String description, BigDecimal price,
            String currency, int available) {}

    record OrderSummary(UUID orderId, String status, BigDecimal total, String currency, Instant createdAt, String items) {}

    /** {@code message} is set only when the payment could not be read, and says so: a missing payment is not unpaid. */
    record PaymentSummary(String status, BigDecimal paid, BigDecimal refunded, BigDecimal refundable, String currency,
            String message) {}

    record ShipmentSummary(String trackingNumber, String status, LocalDate estimatedDelivery, Instant shippedAt,
            Instant deliveredAt, String deliveryProblem) {}

    record RefundSummary(UUID refundRequestId, BigDecimal amount, String status, String reason, String idempotencyKey) {}

    /** That the customer was told something about the order, and when: what an agent needs to avoid telling them twice. */
    record NotificationSummary(Instant sentAt, String sentBy) {}

    record OrderDetails(UUID orderId, String customerEmail, String status, BigDecimal total, String currency,
            Instant createdAt, String cancellationReason, List<OrderApi.OrderLine> lines, PaymentSummary payment,
            ShipmentSummary shipment, List<RefundSummary> refunds, List<NotificationSummary> notifications) {}

    record RefundResult(UUID refundRequestId, String status, BigDecimal amount, String currency, String message) {}

    record Acknowledgement(UUID id, String message) {}

    static ProductSummary toSummary(CatalogApi.Product product) {
        return new ProductSummary(product.id(), product.sku(), product.name(), product.description(), product.price(),
                product.currency(), product.available());
    }

    /** @param items what was ordered, such as "2 x Headlamp, 1 x Tent"; null when not shown */
    static OrderSummary toSummary(OrderApi.Order order, String items) {
        return new OrderSummary(order.id(), order.status(), order.total(), order.currency(), order.createdAt(), items);
    }

    static String itemsOf(OrderApi.Order order) {
        return order.lines().stream()
                .map(line -> line.quantity() + " x " + line.productName())
                .collect(Collectors.joining(", "));
    }

    static PaymentSummary toSummary(PaymentApi.Payment payment) {
        return new PaymentSummary(payment.status(), payment.amount(), payment.refundedAmount(), payment.refundable(),
                payment.currency(), null);
    }

    /** The payment service could not be reached, so whether the order is paid is unknown, not "no". */
    static PaymentSummary paymentUnknown(String message) {
        return new PaymentSummary("UNKNOWN", null, null, null, null, message);
    }

    static ShipmentSummary toSummary(ShippingApi.Shipment shipment) {
        return new ShipmentSummary(shipment.trackingNumber(), shipment.status(), shipment.estimatedDelivery(),
                shipment.shippedAt(), shipment.deliveredAt(), shipment.deliveryProblem());
    }

    static RefundSummary toSummary(RefundRequest refund) {
        return new RefundSummary(refund.getId(), refund.getAmount(), refund.getStatus().name(), refund.getReason(),
                refund.getIdempotencyKey());
    }

    static NotificationSummary toSummary(CustomerNotification notification) {
        return new NotificationSummary(notification.getCreatedAt(), notification.getSentBy());
    }

    static OrderDetails toDetails(OrderApi.Order order, PaymentSummary payment, ShipmentSummary shipment,
            List<RefundRequest> refunds, List<CustomerNotification> notifications) {
        return new OrderDetails(order.id(), order.customerEmail(), order.status(), order.total(), order.currency(),
                order.createdAt(), order.cancellationReason(), order.lines(), payment, shipment,
                refunds.stream().map(ToolViews::toSummary).toList(),
                notifications.stream().map(ToolViews::toSummary).toList());
    }

    /** The refund's outcome, with what the agent should do next. */
    static RefundResult toResult(RefundRequest request) {
        return new RefundResult(request.getId(), request.getStatus().name(), request.getAmount(),
                request.getCurrency(), messageFor(request));
    }

    private static String messageFor(RefundRequest request) {
        return switch (request.getStatus()) {
            case EXECUTED -> "Refunded " + request.getAmount() + " " + request.getCurrency() + ".";
            case PENDING_APPROVAL -> "This refund is above the approval limit and is waiting for a human to approve it. "
                    + "Do not retry it. Tell the customer it is being reviewed.";
            case FAILED -> "The refund did not go through: " + request.getFailure() + " Retrying with the same "
                    + "idempotency key is safe. If it keeps failing, hand it to a person instead of guessing.";
            case REJECTED -> "A human rejected this refund: " + request.getDecisionNote();
        };
    }
}
