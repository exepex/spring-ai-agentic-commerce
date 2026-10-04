package io.github.exepex.commerce.agent;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the shopping assistant remembers of each conversation: its last messages, in this service's database, so any
 * instance can continue a conversation and a restart loses none. A conversation belongs to the customer who started
 * it, and one nobody has written to for {@code commerce.agents.conversation-idle-limit} is forgotten.
 */
@Slf4j
@Component
class ConversationMemory implements ChatMemory {

    static final int MAX_MESSAGES = 30;
    private static final String EVERY_HOUR = "PT1H";
    /** Held until the transaction ends: two turns of one conversation add their messages one after the other. */
    private static final String LOCK_CONVERSATION = "select pg_advisory_xact_lock(hashtext(:conversationId))";
    /**
     * Deletes the conversations whose newest message is older than {@code idleSince}. Messages keep the time they
     * were first saved, so an active conversation also has old messages: only one with no recent message is idle.
     */
    private static final String DELETE_IDLE = """
            delete from spring_ai_chat_memory
            where conversation_id in (
                select old.conversation_id
                from spring_ai_chat_memory old
                where old."timestamp" < :idleSince
                  and not exists (select 1 from spring_ai_chat_memory recent
                                  where recent.conversation_id = old.conversation_id
                                    and recent."timestamp" >= :idleSince))""";

    private final ChatMemory window;
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Duration idleLimit;
    private final Clock clock;

    ConversationMemory(ChatMemoryRepository repository, JdbcClient jdbc, PlatformTransactionManager transactionManager,
            AgentProperties properties, Clock clock) {
        this.window = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(MAX_MESSAGES)
                .build();
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.idleLimit = properties.agents().conversationIdleLimit();
        this.clock = clock;
    }

    /**
     * The key the customer's conversation is remembered under. Another customer who sends the same conversation id
     * gets a conversation of their own. A conversation id holds no colon ({@code ChatRequest}), so no two customers'
     * keys can be equal.
     */
    static String key(String customerEmail, String conversationId) {
        return conversationId + ":" + customerEmail;
    }

    @Override
    public List<Message> get(String conversationId) {
        return window.get(conversationId);
    }

    /**
     * Adds the messages to the conversation: reading it and writing it back happen under the conversation's lock, so
     * two turns at once neither lose nor repeat a message. If the database cannot take them, the turn still answers:
     * the agent may already have acted, and the customer must hear about it. The conversation then lacks the turn.
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        try {
            transactions.executeWithoutResult(transaction -> {
                jdbc.sql(LOCK_CONVERSATION).param("conversationId", conversationId).query().singleRow();
                window.add(conversationId, messages);
            });
        } catch (DataAccessException | TransactionException unsaved) {
            log.warn("Could not save {} messages of a conversation; the turn is answered without them",
                    messages.size(), unsaved);
        }
    }

    @Override
    public void clear(String conversationId) {
        window.clear(conversationId);
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
