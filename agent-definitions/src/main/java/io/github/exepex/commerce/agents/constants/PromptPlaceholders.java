package io.github.exepex.commerce.agents.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The placeholders in an agent's instructions that are filled in when its system prompt is built. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromptPlaceholders {

    /** Where the Slack step goes in the instructions; {@value #SLACK_CHANNEL} inside it becomes the channel id. */
    public static final String SLACK_STEP = "{slackStep}";
    public static final String SLACK_CHANNEL = "{slackChannelId}";
}
