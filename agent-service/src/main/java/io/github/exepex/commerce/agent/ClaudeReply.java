package io.github.exepex.commerce.agent;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Reads the answer out of a Claude response. With thinking on, Spring AI returns each thinking block as a generation
 * of its own, ahead of the generation that holds the answer, so {@link ChatResponse#getResult()} would return the
 * model's private reasoning (or nothing) instead of its reply.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ClaudeReply {

    static String textOf(ChatResponse response) {
        var generations = response.getResults();
        return generations.isEmpty() ? null : generations.getLast().getOutput().getText();
    }
}
