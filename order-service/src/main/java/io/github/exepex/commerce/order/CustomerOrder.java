package io.github.exepex.commerce.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A customer's order, from checkout through payment to what the carrier reports. */
@Entity
@Table(name = "customer_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerOrder {

    @Id
    private UUID id;

    @Column(name = "customer_email")
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    private String currency;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "payment_failure")
    private String paymentFailure;

    @Column(name = "payment_method")
    @Getter(AccessLevel.PACKAGE)
    private String paymentMethod;

    @Version
    @Getter(AccessLevel.NONE)
    private Long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLine> lines = new ArrayList<>();

    private CustomerOrder(UUID id, String customerEmail, String currency, List<OrderLine> lines, String paymentMethod,
            Instant createdAt) {
        this.id = id;
        this.customerEmail = customerEmail;
        this.paymentMethod = paymentMethod;
        this.status = OrderStatus.PLACED;
        this.currency = currency;
        this.lines = new ArrayList<>(lines);
        this.totalAmount = lines.stream().map(OrderLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.createdAt = createdAt;
    }

    static CustomerOrder place(UUID id, String customerEmail, String currency, List<OrderLine> lines,
            String paymentMethod, Instant now) {
        return new CustomerOrder(id, customerEmail, currency, lines, paymentMethod, now);
    }

    void markPaymentPending() {
        status = OrderStatus.PAYMENT_PENDING;
    }

    void confirm() {
        status = OrderStatus.CONFIRMED;
    }

    void markPaymentFailed(String reason) {
        status = OrderStatus.PAYMENT_FAILED;
        paymentFailure = reason;
    }

    /**
     * Only a confirmed order can be cancelled. A placed order is still in checkout with its payment in flight, so
     * only checkout itself changes it: a cancellation then could be overwritten by the confirmation that follows.
     */
    boolean isCancellable() {
        return status == OrderStatus.CONFIRMED;
    }

    /** Only a confirmed order ships: from then on it can no longer be cancelled. */
    boolean isShippable() {
        return status == OrderStatus.CONFIRMED;
    }

    boolean hasShipped() {
        return switch (status) {
            case SHIPPED, DELIVERED, DELIVERY_FAILED, LOST -> true;
            case PLACED, PAYMENT_PENDING, CONFIRMED, PAYMENT_FAILED, CANCELLED -> false;
        };
    }

    /** An order id belongs to the customer who placed it; email addresses are compared ignoring case. */
    boolean isPlacedBy(String otherCustomerEmail) {
        return customerEmail.equalsIgnoreCase(otherCustomerEmail);
    }

    void ship() {
        status = OrderStatus.SHIPPED;
    }

    /** Records what the carrier reported for the shipped parcel. */
    void recordCarrierOutcome(OrderStatus outcome) {
        status = outcome;
    }

    void cancel(String reason, Instant now) {
        status = OrderStatus.CANCELLED;
        cancellationReason = reason;
        cancelledAt = now;
    }

    /** A copy: an order's lines are fixed once it is placed. */
    public List<OrderLine> getLines() {
        return List.copyOf(lines);
    }
}
