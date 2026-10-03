package io.github.exepex.commerce.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** What the catalog API answers with, and how products and reservations turn into it. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CatalogViews {

    record ProductView(UUID id, String sku, String name, String description, BigDecimal price, String currency,
            int onHand, int reserved, int available) {}

    record ReservationView(UUID id, UUID orderId, UUID productId, int quantity, String status, Instant createdAt) {}

    static ProductView toView(Product product) {
        return new ProductView(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                product.getPriceAmount(), product.getCurrency(), product.getOnHand(), product.getReserved(),
                product.available());
    }

    static ReservationView toView(StockReservation reservation) {
        return new ReservationView(reservation.getId(), reservation.getOrderId(), reservation.getProductId(),
                reservation.getQuantity(), reservation.getStatus().name(), reservation.getCreatedAt());
    }
}
