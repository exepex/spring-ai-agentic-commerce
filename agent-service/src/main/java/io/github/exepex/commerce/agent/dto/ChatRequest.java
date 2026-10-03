package io.github.exepex.commerce.agent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** One message of a signed-in customer to the shopping assistant, in a conversation. */
public record ChatRequest(@NotBlank String conversationId, @NotBlank @Email String customerEmail,
        @NotBlank String message) {}
