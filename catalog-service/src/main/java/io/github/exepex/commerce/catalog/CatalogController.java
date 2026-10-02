package io.github.exepex.commerce.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
class CatalogController {

    record ProductView(UUID id, String sku, String name, String description, BigDecimal price, String currency,
            int onHand, int reserved, int available) {

        static ProductView of(Product product) {
            return new ProductView(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                    product.getPriceAmount(), product.getCurrency(), product.getOnHand(), product.getReserved(),
                    product.available());
        }
    }

    record ReserveStockRequest(@NotNull UUID orderId, @Positive int quantity) {}

    record ReservationView(UUID id, UUID orderId, UUID productId, int quantity, String status, Instant createdAt) {

        static ReservationView of(StockReservation reservation) {
            return new ReservationView(reservation.getId(), reservation.getOrderId(), reservation.getProductId(),
                    reservation.getQuantity(), reservation.getStatus().name(), reservation.getCreatedAt());
        }
    }

    record AdjustStockRequest(int delta, @NotBlank String reason) {}

    private final ProductRepository products;
    private final StockService stock;

    CatalogController(ProductRepository products, StockService stock) {
        this.products = products;
        this.stock = stock;
    }

    @GetMapping("/products")
    @Transactional(readOnly = true)
    List<ProductView> listProducts() {
        return products.findAllByOrderBySku().stream().map(ProductView::of).toList();
    }

    @GetMapping("/products/{productId}")
    @Transactional(readOnly = true)
    ProductView getProduct(@PathVariable UUID productId) {
        return products.findById(productId).map(ProductView::of)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @PostMapping("/products/{productId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    ReservationView reserveStock(@PathVariable UUID productId, @Valid @RequestBody ReserveStockRequest request) {
        return ReservationView.of(stock.reserve(request.orderId(), productId, request.quantity()));
    }

    @DeleteMapping("/orders/{orderId}/reservations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void releaseOrderReservations(@PathVariable UUID orderId) {
        stock.releaseOrder(orderId);
    }

    @PostMapping("/products/{productId}/stock-adjustments")
    ProductView adjustStock(@PathVariable UUID productId, @Valid @RequestBody AdjustStockRequest request) {
        return ProductView.of(stock.adjustStock(productId, request.delta(), request.reason()));
    }
}
