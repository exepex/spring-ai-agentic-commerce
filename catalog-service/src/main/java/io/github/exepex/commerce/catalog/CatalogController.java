package io.github.exepex.commerce.catalog;

import io.github.exepex.commerce.catalog.CatalogViews.ProductView;
import io.github.exepex.commerce.catalog.CatalogViews.ReservationView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class CatalogController {

    record ReserveStockRequest(@NotNull UUID orderId, @Positive int quantity) {}

    record AdjustStockRequest(int delta, @NotBlank String reason) {}

    private final ProductRepository products;
    private final StockService stock;

    @GetMapping("/products")
    @Transactional(readOnly = true)
    List<ProductView> listProducts() {
        return products.findAllByOrderBySku().stream().map(CatalogViews::toView).toList();
    }

    @GetMapping("/products/{productId}")
    @Transactional(readOnly = true)
    ProductView getProduct(@PathVariable UUID productId) {
        return products.findById(productId).map(CatalogViews::toView)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @PostMapping("/products/{productId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    ReservationView reserveStock(@PathVariable UUID productId, @Valid @RequestBody ReserveStockRequest request) {
        return CatalogViews.toView(stock.reserve(request.orderId(), productId, request.quantity()));
    }

    @DeleteMapping("/orders/{orderId}/reservations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void releaseOrderReservations(@PathVariable UUID orderId) {
        stock.releaseOrder(orderId);
    }

    @PostMapping("/orders/{orderId}/dispatch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void dispatchOrder(@PathVariable UUID orderId) {
        stock.dispatchOrder(orderId);
    }

    @PostMapping("/products/{productId}/stock-adjustments")
    ProductView adjustStock(@PathVariable UUID productId, @Valid @RequestBody AdjustStockRequest request) {
        return CatalogViews.toView(stock.adjustStock(productId, request.delta(), request.reason()));
    }
}
