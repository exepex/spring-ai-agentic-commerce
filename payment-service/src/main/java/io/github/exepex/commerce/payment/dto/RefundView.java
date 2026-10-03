package io.github.exepex.commerce.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One refund of a payment, as the payment API shows it. */
public record RefundView(UUID id, BigDecimal amount, String reason, String idempotencyKey, String providerReference,
        String status, Instant createdAt) {}
