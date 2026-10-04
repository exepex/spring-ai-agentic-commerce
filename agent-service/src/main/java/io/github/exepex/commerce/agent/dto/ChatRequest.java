package io.github.exepex.commerce.agent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One message of a signed-in customer to the shopping assistant, in a conversation.
 *
 * @param conversationId the conversation, such as a UUID: letters, digits and dashes
 * @param customerEmail the signed-in customer
 * @param message what the customer wrote
 */
public record ChatRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String conversationId,
        @NotBlank @Email @Size(max = 254) String customerEmail, @NotBlank String message) {}
