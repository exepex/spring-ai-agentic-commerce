package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class ClaudeReplyTest {

    @Test
    void readsTheAnswerNotTheThinkingBlocksThatComeBeforeIt() {
        var response = new ChatResponse(List.of(
                new Generation(AssistantMessage.builder().content("private reasoning")
                        .properties(Map.of("signature", "sig")).build()),
                new Generation(AssistantMessage.builder().content("").properties(Map.of("signature", "sig")).build()),
                new Generation(new AssistantMessage("Here is your headlamp."))));

        assertThat(ClaudeReply.textOf(response)).isEqualTo("Here is your headlamp.");
    }
}
