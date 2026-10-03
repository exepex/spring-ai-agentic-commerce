package io.github.exepex.commerce.mcp.downstream.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** An order's payment as the payment service answers with it, with what is still refundable. */
public record Payment(UUID id, UUID orderId, BigDecimal amount, BigDecimal refundedAmount, BigDecimal refundable,
        String currency, String status, String provider, String providerReference, String failureMessage,
        List<Refund> refunds) {}
