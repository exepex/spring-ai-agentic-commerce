package io.github.exepex.commerce.order;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The catalog-service endpoints this service calls. The base URL is {@code spring.http.serviceclient.catalog}. */
@HttpExchange("/api")
interface CatalogHttpApi {

    record CatalogProduct(UUID id, String sku, String name, BigDecimal price, String currency, int available) {}

    record ReserveStockRequest(UUID orderId, int quantity) {}

    @GetExchange("/products/{productId}")
    CatalogProduct getProduct(@PathVariable UUID productId);

    @PostExchange("/products/{productId}/reservations")
    void reserveStock(@PathVariable UUID productId, @RequestBody ReserveStockRequest request);

    @DeleteExchange("/orders/{orderId}/reservations")
    void releaseOrderReservations(@PathVariable UUID orderId);
}
