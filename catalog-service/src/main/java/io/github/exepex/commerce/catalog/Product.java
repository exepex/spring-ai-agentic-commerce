package io.github.exepex.commerce.catalog;

import io.github.exepex.commerce.catalog.exception.InsufficientStockException;
import io.github.exepex.commerce.catalog.exception.InvalidStockAdjustmentException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A sellable product and its stock: units physically on hand and units promised to open orders. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    private UUID id;

    private String sku;

    private String name;

    private String description;

    @Column(name = "price_amount")
    private BigDecimal priceAmount;

    private String currency;

    @Column(name = "on_hand")
    private int onHand;

    private int reserved;

    public int available() {
        return Math.max(0, onHand - reserved);
    }

    /** Units promised to open orders that the stock on hand can no longer cover. */
    public int shortfall() {
        return Math.max(0, reserved - onHand);
    }

    void reserve(int quantity) {
        if (quantity > available()) {
            throw new InsufficientStockException(sku, quantity, available());
        }
        reserved += quantity;
    }

    void release(int quantity) {
        reserved -= quantity;
    }

    /** Reserved units leave the warehouse with a shipped order: they are no longer on hand or reserved. */
    void dispatch(int quantity) {
        reserved -= quantity;
        onHand -= quantity;
    }

    /** Units that were picked for an order which did not ship go back on the shelf. */
    void restock(int quantity) {
        onHand += quantity;
    }

    void adjustOnHand(int delta) {
        if (onHand + delta < 0) {
            throw new InvalidStockAdjustmentException(sku, onHand, delta);
        }
        onHand += delta;
    }
}
