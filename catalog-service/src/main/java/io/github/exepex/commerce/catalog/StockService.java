package io.github.exepex.commerce.catalog;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reserves, releases and adjusts stock. Every change locks the product row first. */
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Holds {@code quantity} units for the order. Repeating the same request returns the same reservation. */
    @Transactional
    public StockReservation reserve(UUID orderId, UUID productId, int quantity) {
        Product product = lockProduct(productId);
        var existing = reservations.findByOrderIdAndProductId(orderId, productId);
        if (existing.isPresent()) {
            StockReservation reservation = existing.get();
            if (!reservation.isSameReservationAs(quantity)) {
                throw new ReservationConflictException(orderId, product.getSku(), reservation);
            }
            return reservation;
        }
        product.reserve(quantity);
        return reservations.save(new StockReservation(orderId, productId, quantity, Instant.now(clock)));
    }

    /**
     * Gives back every unit held for the order. Units already dispatched go back on the shelf: that happens only when
     * the order was cancelled while it was being shipped, so it never left. Releasing twice changes nothing.
     */
    @Transactional
    public void releaseOrder(UUID orderId) {
        for (StockReservation reservation : reservations.findByOrderIdOrderByProductId(orderId)) {
            switch (reservation.getStatus()) {
                case RESERVED -> {
                    lockProduct(reservation.getProductId()).release(reservation.getQuantity());
                    reservation.markReleased();
                }
                case DISPATCHED -> {
                    lockProduct(reservation.getProductId()).restock(reservation.getQuantity());
                    reservation.markReleased();
                }
                case RELEASED -> { }
            }
        }
    }

    /**
     * Hands the order's reserved units to shipping: they leave both the stock on hand and the reservations, so a later
     * stock-out never names an order that has shipped. Refused when the stock was released, or when a stock-out left
     * the order uncovered: those units are not in the warehouse. Dispatching twice changes nothing.
     */
    @Transactional
    public void dispatchOrder(UUID orderId) {
        List<StockReservation> held = reservations.findByOrderIdOrderByProductId(orderId);
        if (held.isEmpty()) {
            throw DispatchRefusedException.nothingReserved(orderId);
        }
        if (held.stream().anyMatch(reservation -> reservation.getStatus() == StockReservation.Status.RELEASED)) {
            throw DispatchRefusedException.released(orderId);
        }
        for (StockReservation reservation : held) {
            if (reservation.getStatus() == StockReservation.Status.RESERVED) {
                Product product = lockProduct(reservation.getProductId());
                if (newestOrdersCovering(product).contains(orderId)) {
                    throw DispatchRefusedException.stockShort(orderId, product.getSku());
                }
                product.dispatch(reservation.getQuantity());
                reservation.markDispatched();
            }
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
            events.publishEvent(StockOutEvent.of(product, reason, newestOrdersCovering(product), Instant.now(clock)));
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
