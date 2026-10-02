package io.github.exepex.commerce.mcp.downstream;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/shipments")
public interface ShippingApi {

    record Shipment(UUID id, UUID orderId, String trackingNumber, String status, LocalDate estimatedDelivery,
            Instant createdAt, Instant cancelledAt) {}

    @GetExchange("/{orderId}")
    Shipment getShipment(@PathVariable UUID orderId);
}
