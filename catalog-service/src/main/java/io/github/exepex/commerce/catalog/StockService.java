package io.github.exepex.commerce.catalog;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reserves, releases and adjusts stock. Every change locks the product row first. */
@Service
public class StockService {

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    StockService(ProductRepository products, StockReservationRepository reservations,
            ApplicationEventPublisher events, Clock clock) {
        this.products = products;
        this.reservations = reservations;
        this.events = events;
        this.clock = clock;
    }

    /** Holds {@code quantity} units for the order. Repeating the same request returns the same reservation. */
    @Transactional
    public StockReservation reserve(UUID orderId, UUID productId, int quantity) {
        Product product = lockProduct(productId);
        var existing = reservations.findByOrderIdAndProductId(orderId, productId);
        if (existing.isPresent()) {
            StockReservation reservation = existing.get();
            if (reservation.getStatus() == StockReservation.Status.RESERVED && reservation.getQuantity() == quantity) {
                return reservation;
            }
            throw new ReservationConflictException(orderId, product.getSku(), reservation);
        }
        product.reserve(quantity);
        return reservations.save(new StockReservation(orderId, productId, quantity, Instant.now(clock)));
    }

    /** Gives back every unit held for the order. Releasing twice changes nothing. */
    @Transactional
    public void releaseOrder(UUID orderId) {
        for (StockReservation reservation : reservations.findByOrderIdAndStatus(orderId, StockReservation.Status.RESERVED)) {
            lockProduct(reservation.getProductId()).release(reservation.getQuantity());
            reservation.markReleased();
        }
    }

    /**
     * Changes the units on hand, for a delivery (positive) or a write-off such as damaged goods (negative).
     *
     * <p>When the units left no longer cover open reservations, a {@link StockOutEvent} names the newest orders
     * that cannot be fulfilled. The reservations stay in place: deciding what to do about those orders is not the
     * catalog's job.
     */
    @Transactional
    public Product adjustStock(UUID productId, int delta, String reason) {
        Product product = lockProduct(productId);
        product.adjustOnHand(delta);
        if (delta < 0 && product.shortfall() > 0) {
            events.publishEvent(new StockOutEvent(UUID.randomUUID(), Instant.now(clock), product.getId(),
                    product.getSku(), product.getOnHand(), product.getReserved(), product.shortfall(), reason,
                    newestOrdersCovering(product)));
        }
        return product;
    }

    private List<UUID> newestOrdersCovering(Product product) {
        List<UUID> affectedOrderIds = new ArrayList<>();
        int unitsCovered = 0;
        for (StockReservation reservation : reservations.findByProductIdAndStatusOrderByCreatedAtDesc(
                product.getId(), StockReservation.Status.RESERVED)) {
            if (unitsCovered >= product.shortfall()) {
                break;
            }
            affectedOrderIds.add(reservation.getOrderId());
            unitsCovered += reservation.getQuantity();
        }
        return affectedOrderIds;
    }

    private Product lockProduct(UUID productId) {
        return products.findForUpdate(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
