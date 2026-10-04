package io.github.exepex.commerce.agent;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * What the shopping assistant remembers of each conversation: its last messages, in this service's database, so any
 * instance can continue a conversation and a restart loses none. A conversation belongs to the customer who started
 * it, and one nobody has written to for {@code commerce.agents.conversation-idle-limit} is forgotten.
 */
@Slf4j
@Component
class ConversationMemory {

    static final int MAX_MESSAGES = 30;
    private static final String EVERY_HOUR = "PT1H";
    /** Every save rewrites a conversation's messages with the time of the save: old rows are idle conversations. */
    private static final String DELETE_IDLE = "delete from spring_ai_chat_memory where \"timestamp\" < :idleSince";

    private final ChatMemory memory;
    private final JdbcClient jdbc;
    private final Duration idleLimit;
    private final Clock clock;

    ConversationMemory(ChatMemoryRepository repository, JdbcClient jdbc, AgentProperties properties, Clock clock) {
        this.memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MESSAGES)
                .build();
        this.jdbc = jdbc;
        this.idleLimit = properties.agents().conversationIdleLimit();
        this.clock = clock;
    }

    ChatMemory memory() {
        return memory;
    }

    /**
     * The key the customer's conversation is remembered under. Another customer who sends the same conversation id
     * gets a conversation of their own. A conversation id holds no colon ({@code ChatRequest}), so no two customers'
     * keys can be equal.
     */
    static String key(String customerEmail, String conversationId) {
        return conversationId + ":" + customerEmail;
    }

    /** Every instance may run this at once: deleting the same rows twice is harmless, so it takes no lock. */
    @Scheduled(fixedDelayString = EVERY_HOUR, initialDelayString = EVERY_HOUR)
    void forgetIdleConversations() {
        var deleted = jdbc.sql(DELETE_IDLE)
                .param("idleSince", Timestamp.from(Instant.now(clock).minus(idleLimit)))
                .update();
        log.debug("Forgot {} messages of idle conversations", deleted);
    }
}
