package io.github.exepex.commerce.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order's payment, what is still refundable, and its refunds, as the payment API shows it. */
public record PaymentView(UUID id, UUID orderId, String customerEmail, BigDecimal amount, BigDecimal refundedAmount,
        BigDecimal refundable, String currency, String status, String provider, String providerReference,
        String failureMessage, Instant createdAt, List<RefundView> refunds) {}
