package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.OrderViews.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequiredArgsConstructor
class OrderController {

    record LineRequest(@NotNull UUID productId, @Positive int quantity) {}

    /**
     * {@code paymentMethod} is a Stripe test payment method; it defaults to the test Visa card. {@code orderId} is
     * optional: with it, placing the order is idempotent.
     */
    record PlaceOrderRequest(UUID orderId, @NotBlank @Email String customerEmail, @NotEmpty List<@Valid LineRequest> lines,
            String paymentMethod) {

        String paymentMethodOrDefault() {
            return paymentMethod == null || paymentMethod.isBlank() ? "pm_card_visa" : paymentMethod;
        }

        List<OrderService.RequestedLine> requestedLines() {
            return lines.stream().map(line -> new OrderService.RequestedLine(line.productId(), line.quantity())).toList();
        }
    }

    record CancelOrderRequest(@NotBlank String reason) {}

    private final OrderService orderService;

    /**
     * Answers 201 when the order is paid, 202 when it is not settled yet (its payment is pending, or the same order is
     * still being placed by another request), and 402 when the card was declined.
     */
    @PostMapping
    ResponseEntity<OrderView> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        CustomerOrder order = orderService.placeOrder(request.orderId(), request.customerEmail(),
                request.requestedLines(), request.paymentMethodOrDefault());
        HttpStatus status = order.getStatus() == OrderStatus.CONFIRMED ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).location(URI.create("/api/orders/" + order.getId()))
                .body(OrderViews.toView(order));
    }

    @GetMapping("/{orderId}")
    OrderView getOrder(@PathVariable UUID orderId) {
        return OrderViews.toView(orderService.getOrder(orderId));
    }

    /** A customer's orders when {@code customerEmail} is given, otherwise the 100 most recent orders. */
    @GetMapping
    List<OrderView> findOrders(@RequestParam(required = false) String customerEmail) {
        List<CustomerOrder> found = customerEmail == null
                ? orderService.findRecentOrders()
                : orderService.findOrdersOf(customerEmail);
        return found.stream().map(OrderViews::toView).toList();
    }

    /** Ships the order from the warehouse. Shipping twice changes nothing; a cancelled order is refused. */
    @PostMapping("/{orderId}/dispatch")
    OrderView shipOrder(@PathVariable UUID orderId) {
        return OrderViews.toView(orderService.shipOrder(orderId));
    }

    @PostMapping("/{orderId}/cancellation")
    OrderView cancelOrder(@PathVariable UUID orderId, @Valid @RequestBody CancelOrderRequest request) {
        return OrderViews.toView(orderService.cancelOrder(orderId, request.reason()));
    }
}
