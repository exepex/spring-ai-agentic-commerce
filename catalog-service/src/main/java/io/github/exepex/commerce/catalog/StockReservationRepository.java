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
     * Locks the rows it returns, so a concurrent release of the same order waits and then no longer finds them
     * reserved: each reservation's units are given back once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StockReservation> findByOrderIdAndStatus(UUID orderId, StockReservation.Status status);

    List<StockReservation> findByProductIdAndStatusOrderByCreatedAtDesc(
            UUID productId, StockReservation.Status status);
}
