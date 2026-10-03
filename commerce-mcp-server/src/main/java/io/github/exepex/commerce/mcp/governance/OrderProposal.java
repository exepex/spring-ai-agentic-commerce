package io.github.exepex.commerce.mcp.governance;

import io.github.exepex.commerce.mcp.governance.dto.ProposedLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An order an agent put together for a customer. The agent cannot place it: only the customer's own confirmation in
 * the UI turns it into an order and charges the card.
 */
@Entity
@Table(name = "order_proposal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderProposal {

    public enum Status {
        PROPOSED,
        /**
         * The customer confirmed it and the order is being placed, or its payment is not settled yet. A second
         * confirmation finds it taken; {@link ProposalReconciler} places the same order again until it settles.
         */
        CONFIRMING,
        CONFIRMED,
        FAILED
    }

    @Id
    private UUID id;

    @Column(name = "customer_email")
    private String customerEmail;

    /** JSON array of {@link ProposedLine}. */
    private String lines;

    private BigDecimal total;

    private String currency;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "order_id")
    private UUID orderId;

    private String failure;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "confirming_since")
    private Instant confirmingSince;

    @Column(name = "created_at")
    private Instant createdAt;

    OrderProposal(String customerEmail, String lines, BigDecimal total, String currency, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.customerEmail = customerEmail;
        this.lines = lines;
        this.total = total;
        this.currency = currency;
        this.status = Status.PROPOSED;
        this.createdAt = createdAt;
    }
}
