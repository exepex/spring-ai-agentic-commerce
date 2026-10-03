package io.github.exepex.commerce.catalog;

import io.github.exepex.commerce.catalog.dto.ProductView;
import io.github.exepex.commerce.catalog.dto.ReservationView;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** How products and reservations are shown through the catalog API. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CatalogMapper {

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
