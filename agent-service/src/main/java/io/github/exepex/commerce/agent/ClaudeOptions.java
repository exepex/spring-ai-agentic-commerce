package io.github.exepex.commerce.agent;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import io.github.exepex.commerce.agent.constants.ClaudeSettings;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.ai.anthropic.AnthropicChatOptions;

/**
 * Request options for Claude. Thinking is adaptive (the model decides how much to think) and its depth is set with
 * {@code effort}; no sampling parameters are sent, since current Claude models reject them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ClaudeOptions {

    static AnthropicChatOptions.Builder forAgent(String model, String effort) {
        return AnthropicChatOptions.builder()
                .model(Model.of(model))
                .maxTokens(ClaudeSettings.MAX_TOKENS)
                .thinkingAdaptive()
                .effort(OutputConfig.Effort.of(effort));
    }
}
