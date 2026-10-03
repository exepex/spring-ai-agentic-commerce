package io.github.exepex.commerce.order;

import io.github.exepex.commerce.order.constants.ApiPaths;
import io.github.exepex.commerce.order.dto.CatalogProduct;
import io.github.exepex.commerce.order.dto.ReserveStockRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** The catalog-service endpoints this service calls. The base URL is {@code spring.http.serviceclient.catalog}. */
@HttpExchange(ApiPaths.REMOTE_API)
interface CatalogHttpApi {

    @GetExchange(ApiPaths.CATALOG_PRODUCT)
    CatalogProduct getProduct(@PathVariable UUID productId);

    @PostExchange(ApiPaths.CATALOG_PRODUCT_RESERVATIONS)
    void reserveStock(@PathVariable UUID productId, @RequestBody ReserveStockRequest request);

    @DeleteExchange(ApiPaths.CATALOG_ORDER_RESERVATIONS)
    void releaseOrderReservations(@PathVariable UUID orderId);

    @PostExchange(ApiPaths.CATALOG_ORDER_DISPATCH)
    void dispatchOrder(@PathVariable UUID orderId);
}
