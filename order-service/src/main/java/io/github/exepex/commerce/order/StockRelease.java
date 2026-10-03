package io.github.exepex.commerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stock reserved for an order that must go back to the catalog: kept until the catalog confirms the release. Rows are
 * only written by {@link StockReleaseRepository#insertIfAbsent}, so two requests for the same release cannot collide.
 */
@Entity
@Table(name = "stock_release")
@Getter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StockRelease {

    @Id
    @Column(name = "order_id")
    private UUID orderId;
}
