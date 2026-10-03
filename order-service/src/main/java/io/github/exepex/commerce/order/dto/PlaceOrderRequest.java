package io.github.exepex.commerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * An order to place. {@code paymentMethod} is a Stripe test payment method; it defaults to the test Visa card.
 * {@code orderId} is optional: with it, placing the order is idempotent.
 */
public record PlaceOrderRequest(UUID orderId, @NotBlank @Email String customerEmail,
        @NotEmpty List<@Valid LineRequest> lines, String paymentMethod) {}
