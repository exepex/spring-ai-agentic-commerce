package io.github.exepex.commerce.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.util.UUID;

/** A sellable product and its stock: units physically on hand and units promised to open orders. */
@Entity
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

    protected Product() {
        // for JPA
    }

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

    void adjustOnHand(int delta) {
        if (onHand + delta < 0) {
            throw new InvalidStockAdjustmentException(sku, onHand, delta);
        }
        onHand += delta;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPriceAmount() {
        return priceAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public int getOnHand() {
        return onHand;
    }

    public int getReserved() {
        return reserved;
    }
}
