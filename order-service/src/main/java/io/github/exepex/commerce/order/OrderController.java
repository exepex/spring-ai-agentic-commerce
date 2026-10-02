package io.github.exepex.commerce.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    record LineRequest(@NotNull UUID productId, @Positive int quantity) {}

    /** {@code paymentMethod} is a Stripe test payment method; it defaults to the test Visa card. */
    record PlaceOrderRequest(@NotBlank @Email String customerEmail, @NotEmpty List<@Valid LineRequest> lines,
            String paymentMethod) {

        String paymentMethodOrDefault() {
            return paymentMethod == null || paymentMethod.isBlank() ? "pm_card_visa" : paymentMethod;
        }
    }

    record CancelOrderRequest(@NotBlank String reason) {}

    record LineView(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice,
            BigDecimal lineTotal) {

        static LineView of(OrderLine line) {
            return new LineView(line.getProductId(), line.getSku(), line.getProductName(), line.getQuantity(),
                    line.getUnitPrice(), line.lineTotal());
        }
    }

    record OrderView(UUID id, String customerEmail, OrderStatus status, BigDecimal total, String currency,
            Instant createdAt, Instant cancelledAt, String cancellationReason, String paymentFailure,
            List<LineView> lines) {

        static OrderView of(CustomerOrder order) {
            return new OrderView(order.getId(), order.getCustomerEmail(), order.getStatus(), order.getTotalAmount(),
                    order.getCurrency(), order.getCreatedAt(), order.getCancelledAt(), order.getCancellationReason(),
                    order.getPaymentFailure(), order.getLines().stream().map(LineView::of).toList());
        }
    }

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderView> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        CustomerOrder order = orderService.placeOrder(request.customerEmail(), request.lines().stream()
                .map(line -> new OrderService.RequestedLine(line.productId(), line.quantity()))
                .toList(), request.paymentMethodOrDefault());
        return ResponseEntity.created(URI.create("/api/orders/" + order.getId())).body(OrderView.of(order));
    }

    @GetMapping("/{orderId}")
    OrderView getOrder(@PathVariable UUID orderId) {
        return OrderView.of(orderService.getOrder(orderId));
    }

    /** A customer's orders when {@code customerEmail} is given, otherwise the 100 most recent orders. */
    @GetMapping
    List<OrderView> findOrders(@RequestParam(required = false) String customerEmail) {
        List<CustomerOrder> found = customerEmail == null
                ? orderService.findRecentOrders()
                : orderService.findOrdersOf(customerEmail);
        return found.stream().map(OrderView::of).toList();
    }

    @PostMapping("/{orderId}/cancellation")
    OrderView cancelOrder(@PathVariable UUID orderId, @Valid @RequestBody CancelOrderRequest request) {
        return OrderView.of(orderService.cancelOrder(orderId, request.reason()));
    }
}
