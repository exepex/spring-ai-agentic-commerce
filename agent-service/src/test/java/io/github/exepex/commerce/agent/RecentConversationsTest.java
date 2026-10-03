package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

class RecentConversationsTest {

    @Test
    void forgetsTheLeastRecentlyUsedConversationOnceTheLimitIsReached() {
        var conversations = new RecentConversations(2);
        conversations.saveAll("first", List.of(new UserMessage("hi")));
        conversations.saveAll("second", List.of(new UserMessage("hello")));
        conversations.findByConversationId("first");

        conversations.saveAll("third", List.of(new UserMessage("hey")));

        assertThat(conversations.findConversationIds()).containsExactlyInAnyOrder("first", "third");
        assertThat(conversations.findByConversationId("second")).isEmpty();
    }
}
