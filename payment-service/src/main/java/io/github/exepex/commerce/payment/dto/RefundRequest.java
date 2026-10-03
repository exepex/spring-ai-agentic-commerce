package io.github.exepex.commerce.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A refund of part or all of an order's payment. The reason and the key are stored with the refund, so they are limited
 * to what their columns hold.
 */
public record RefundRequest(@NotNull @Positive BigDecimal amount, @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 200) String idempotencyKey) {}
