package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exepex.commerce.agent.dto.ChatRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ChatRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aConversationIdLikeTheShopsUuidIsAccepted() {
        assertThat(validator.validate(new ChatRequest("6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c", "ana@example.com",
                "Hello"))).isEmpty();
    }

    /** A colon would let one customer's conversation key look like another's (ConversationMemory.key). */
    @Test
    void aConversationIdWithAColonOrTooLongIsRefused() {
        assertThat(validator.validate(new ChatRequest("x:ana@example.com", "ben@example.com", "Hello"))).isNotEmpty();
        assertThat(validator.validate(new ChatRequest("a".repeat(65), "ben@example.com", "Hello"))).isNotEmpty();
    }
}
