package io.github.exepex.commerce.catalog;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    Optional<StockReservation> findByOrderIdAndProductId(UUID orderId, UUID productId);

    /**
     * Locks the rows it returns, so a concurrent release or dispatch of the same order waits and then sees what the
     * other did: each reservation's units are given back or dispatched once. Ordered by product, so two orders lock
     * their products in the same order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StockReservation> findByOrderIdOrderByProductId(UUID orderId);

    List<StockReservation> findByProductIdAndStatusOrderByCreatedAtDesc(
            UUID productId, StockReservation.Status status);
}
