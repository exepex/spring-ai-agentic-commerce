package io.github.exepex.commerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One product on an order, with the name and price as they were when the order was placed. */
@Entity
@Table(name = "order_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine {

    @Id
    @Getter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    private String sku;

    @Column(name = "product_name")
    private String productName;

    private int quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    OrderLine(UUID productId, String sku, String productName, int quantity, BigDecimal unitPrice) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
