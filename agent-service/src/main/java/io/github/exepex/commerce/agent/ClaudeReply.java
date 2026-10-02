package io.github.exepex.commerce.agent;

import java.util.List;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Reads the answer out of a Claude response. With thinking on, Spring AI returns each thinking block as a generation
 * of its own, ahead of the generation that holds the answer, so {@link ChatResponse#getResult()} would return the
 * model's private reasoning (or nothing) instead of its reply.
 */
final class ClaudeReply {

    private ClaudeReply() {}

    static String textOf(ChatResponse response) {
        List<Generation> generations = response.getResults();
        return generations.isEmpty() ? null : generations.getLast().getOutput().getText();
    }
}
