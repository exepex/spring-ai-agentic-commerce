package io.github.exepex.commerce.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** A card charge for one order. */
public record ChargeRequest(@NotNull UUID orderId, @NotBlank @Email String customerEmail,
        @NotNull @Positive BigDecimal amount, @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank String paymentMethod) {}
