package io.github.exepex.commerce.mcp.governance.dto;

import io.github.exepex.commerce.mcp.governance.OrderProposal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order an agent proposed, as the customer and the agent see it. */
public record Proposal(UUID id, String customerEmail, List<ProposedLine> lines, BigDecimal total, String currency,
        OrderProposal.Status status, UUID orderId, String failure, Instant createdAt) {}
