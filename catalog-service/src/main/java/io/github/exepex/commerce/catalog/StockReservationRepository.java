package io.github.exepex.commerce.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    Optional<StockReservation> findByOrderIdAndProductId(UUID orderId, UUID productId);

    List<StockReservation> findByOrderIdAndStatus(UUID orderId, StockReservation.Status status);

    List<StockReservation> findByProductIdAndStatusOrderByCreatedAtDesc(
            UUID productId, StockReservation.Status status);
}
