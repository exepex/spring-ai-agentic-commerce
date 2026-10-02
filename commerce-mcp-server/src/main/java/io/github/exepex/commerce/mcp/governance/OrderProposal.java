package io.github.exepex.commerce.mcp.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An order an agent put together for a customer. The agent cannot place it: only the customer's own confirmation in
 * the UI turns it into an order and charges the card.
 */
@Entity
@Table(name = "order_proposal")
public class OrderProposal {

    public enum Status {
        PROPOSED,
        CONFIRMED,
        FAILED
    }

    @Id
    private UUID id;

    @Column(name = "customer_email")
    private String customerEmail;

    /** JSON array of {@link ProposalService.ProposedLine}. */
    private String lines;

    private BigDecimal total;

    private String currency;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "order_id")
    private UUID orderId;

    private String failure;

    @Column(name = "created_at")
    private Instant createdAt;

    protected OrderProposal() {
        // for JPA
    }

    OrderProposal(String customerEmail, String lines, BigDecimal total, String currency, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.customerEmail = customerEmail;
        this.lines = lines;
        this.total = total;
        this.currency = currency;
        this.status = Status.PROPOSED;
        this.createdAt = createdAt;
    }

    void markConfirmed(UUID placedOrderId) {
        status = Status.CONFIRMED;
        orderId = placedOrderId;
        failure = null;
    }

    void markFailed(String message) {
        status = Status.FAILED;
        failure = message;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getLines() {
        return lines;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getFailure() {
        return failure;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
