package io.github.exepex.commerce.mcp.tools.dto;

import java.math.BigDecimal;

/** {@code message} is set only when the payment could not be read, and says so: a missing payment is not unpaid. */
public record PaymentSummary(String status, BigDecimal paid, BigDecimal refunded, BigDecimal refundable,
        String currency, String message) {}
