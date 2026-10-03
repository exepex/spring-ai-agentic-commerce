package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.ApiPaths;
import io.github.exepex.commerce.order.dto.CancelOrderRequest;
import io.github.exepex.commerce.order.dto.OrderView;
import io.github.exepex.commerce.order.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
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
@RequestMapping(ApiPaths.ORDERS)
@RequiredArgsConstructor
class OrderController {

    private final OrderService orderService;

    /**
     * Answers 201 when the order is paid, 202 when it is not settled yet (its payment is pending, or the same order is
     * still being placed by another request), and 402 when the card was declined.
     */
    @PostMapping
    ResponseEntity<OrderView> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        var order = orderService.placeOrder(request.orderId(), request.customerEmail(),
                OrderMapper.requestedLinesOf(request), OrderMapper.paymentMethodOf(request));
        var status = order.getStatus() == OrderStatus.CONFIRMED ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).location(URI.create(ApiPaths.ORDER_LOCATION.formatted(order.getId())))
                .body(OrderMapper.toView(order));
    }

    @GetMapping(ApiPaths.ORDER)
    OrderView getOrder(@PathVariable UUID orderId) {
        return OrderMapper.toView(orderService.getOrder(orderId));
    }

    /** A customer's orders when {@code customerEmail} is given, otherwise the 100 most recent orders. */
    @GetMapping
    List<OrderView> findOrders(@RequestParam(required = false) String customerEmail) {
        var found = customerEmail == null
                ? orderService.findRecentOrders()
                : orderService.findOrdersOf(customerEmail);
        return found.stream().map(OrderMapper::toView).toList();
    }

    /** Ships the order from the warehouse. Shipping twice changes nothing; a cancelled order is refused. */
    @PostMapping(ApiPaths.ORDER_DISPATCH)
    OrderView shipOrder(@PathVariable UUID orderId) {
        return OrderMapper.toView(orderService.shipOrder(orderId));
    }

    @PostMapping(ApiPaths.ORDER_CANCELLATION)
    OrderView cancelOrder(@PathVariable UUID orderId, @Valid @RequestBody CancelOrderRequest request) {
        return OrderMapper.toView(orderService.cancelOrder(orderId, request.reason()));
    }
}
