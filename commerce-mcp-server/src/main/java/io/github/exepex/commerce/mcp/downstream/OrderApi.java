package io.github.exepex.commerce.mcp.downstream;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/orders")
public interface OrderApi {

    record OrderLine(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice,
            BigDecimal lineTotal) {}

    record Order(UUID id, String customerEmail, String status, BigDecimal total, String currency, Instant createdAt,
            Instant cancelledAt, String cancellationReason, String paymentFailure, List<OrderLine> lines) {}

    record RequestedLine(UUID productId, int quantity) {}

    record PlaceOrderRequest(String customerEmail, List<RequestedLine> lines, String paymentMethod) {}

    record CancelOrderRequest(String reason) {}

    @GetExchange("/{orderId}")
    Order getOrder(@PathVariable UUID orderId);

    @GetExchange
    List<Order> findOrders(@RequestParam(required = false) String customerEmail);

    @PostExchange
    Order placeOrder(@RequestBody PlaceOrderRequest request);

    @PostExchange("/{orderId}/cancellation")
    Order cancelOrder(@PathVariable UUID orderId, @RequestBody CancelOrderRequest request);
}
