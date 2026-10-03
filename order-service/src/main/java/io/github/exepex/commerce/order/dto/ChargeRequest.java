package io.github.exepex.commerce.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Asks the payment service to charge an order's total. */
public record ChargeRequest(UUID orderId, String customerEmail, BigDecimal amount, String currency,
        String paymentMethod) {}
