package io.github.exepex.commerce.mcp.tools;

import io.github.exepex.commerce.mcp.constants.DownstreamApis;
import io.github.exepex.commerce.mcp.constants.ToolMessages;
import io.github.exepex.commerce.mcp.downstream.dto.Order;
import io.github.exepex.commerce.mcp.downstream.dto.Payment;
import io.github.exepex.commerce.mcp.downstream.dto.Product;
import io.github.exepex.commerce.mcp.downstream.dto.Shipment;
import io.github.exepex.commerce.mcp.governance.CustomerNotification;
import io.github.exepex.commerce.mcp.governance.RefundRequest;
import io.github.exepex.commerce.mcp.tools.dto.NotificationSummary;
import io.github.exepex.commerce.mcp.tools.dto.OrderDetails;
import io.github.exepex.commerce.mcp.tools.dto.OrderSummary;
import io.github.exepex.commerce.mcp.tools.dto.PaymentSummary;
import io.github.exepex.commerce.mcp.tools.dto.ProductSummary;
import io.github.exepex.commerce.mcp.tools.dto.RefundResult;
import io.github.exepex.commerce.mcp.tools.dto.RefundSummary;
import io.github.exepex.commerce.mcp.tools.dto.ShipmentSummary;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How the services' answers and the shop's records turn into what the shop's tools answer an agent with. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ToolMapper {

    static ProductSummary toSummary(Product product) {
        return new ProductSummary(product.id(), product.sku(), product.name(), product.description(), product.price(),
                product.currency(), product.available());
    }

    /** @param items what was ordered, such as "2 x Headlamp, 1 x Tent"; null when not shown */
    static OrderSummary toSummary(Order order, String items) {
        return new OrderSummary(order.id(), order.status(), order.total(), order.currency(), order.createdAt(), items);
    }

    static String itemsOf(Order order) {
        return order.lines().stream()
                .map(line -> ToolMessages.ORDER_ITEM.formatted(line.quantity(), line.productName()))
                .collect(Collectors.joining(ToolMessages.ORDER_ITEM_SEPARATOR));
    }

    static PaymentSummary toSummary(Payment payment) {
        return new PaymentSummary(payment.status(), payment.amount(), payment.refundedAmount(), payment.refundable(),
                payment.currency(), null);
    }

    /** The payment service could not be reached, so whether the order is paid is unknown, not "no". */
    static PaymentSummary paymentUnknown(String message) {
        return new PaymentSummary(DownstreamApis.PAYMENT_STATUS_UNKNOWN, null, null, null, null, message);
    }

    static ShipmentSummary toSummary(Shipment shipment) {
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

    static OrderDetails toDetails(Order order, PaymentSummary payment, ShipmentSummary shipment,
            List<RefundRequest> refunds, List<CustomerNotification> notifications) {
        return new OrderDetails(order.id(), order.customerEmail(), order.status(), order.total(), order.currency(),
                order.createdAt(), order.cancellationReason(), order.lines(), payment, shipment,
                refunds.stream().map(ToolMapper::toSummary).toList(),
                notifications.stream().map(ToolMapper::toSummary).toList());
    }

    /** The refund's outcome, with what the agent should do next. */
    static RefundResult toResult(RefundRequest request) {
        return new RefundResult(request.getId(), request.getStatus().name(), request.getAmount(),
                request.getCurrency(), messageFor(request));
    }

    private static String messageFor(RefundRequest request) {
        return switch (request.getStatus()) {
            case EXECUTED -> ToolMessages.REFUND_EXECUTED.formatted(request.getAmount(), request.getCurrency());
            case PENDING_APPROVAL -> ToolMessages.REFUND_PENDING_APPROVAL;
            case FAILED -> ToolMessages.REFUND_FAILED.formatted(request.getFailure());
            case REJECTED -> ToolMessages.REFUND_REJECTED.formatted(request.getDecisionNote());
        };
    }
}
