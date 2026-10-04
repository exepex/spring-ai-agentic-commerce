package io.github.exepex.commerce.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface StockReleaseRepository extends JpaRepository<StockRelease, UUID> {

    /** Records the release unless it is already recorded, in one statement, so two at once cannot collide. */
    @Modifying
    @Query(value = "insert into orders.stock_release (order_id, requested_at) values (:orderId, :now) on conflict do nothing",
            nativeQuery = true)
    void insertIfAbsent(UUID orderId, Instant now);

    /** The oldest releases still waiting, a bounded batch so one run stays within its lock. */
    List<StockRelease> findTop50ByOrderByRequestedAt();
}
