package io.github.exepex.commerce.mcp.downstream;

import io.github.exepex.commerce.mcp.constants.DownstreamApis;
import io.github.exepex.commerce.mcp.downstream.dto.CancelOrderRequest;
import io.github.exepex.commerce.mcp.downstream.dto.Order;
import io.github.exepex.commerce.mcp.downstream.dto.PlaceOrderRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(DownstreamApis.ORDERS)
public interface OrderApi {

    @GetExchange(DownstreamApis.BY_ORDER_ID)
    Order getOrder(@PathVariable UUID orderId);

    @GetExchange
    List<Order> findOrders(@RequestParam(required = false) String customerEmail);

    /** The order comes back {@code CONFIRMED}, or not settled yet when its payment is still pending. */
    @PostExchange
    Order placeOrder(@RequestBody PlaceOrderRequest request);

    @PostExchange(DownstreamApis.ORDER_CANCELLATION)
    Order cancelOrder(@PathVariable UUID orderId, @RequestBody CancelOrderRequest request);
}
