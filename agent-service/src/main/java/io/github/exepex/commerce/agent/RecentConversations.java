package io.github.exepex.commerce.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

/**
 * Keeps the messages of the most recently used conversations in memory and forgets the least recently used one once
 * the limit is reached, so the chat cannot fill the heap however many conversations are started.
 */
final class RecentConversations implements ChatMemoryRepository {

    private final Map<String, List<Message>> conversations;

    RecentConversations(int maxConversations) {
        this.conversations = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
                return size() > maxConversations;
            }
        };
    }

    @Override
    public synchronized List<String> findConversationIds() {
        return new ArrayList<>(conversations.keySet());
    }

    @Override
    public synchronized List<Message> findByConversationId(String conversationId) {
        return List.copyOf(conversations.getOrDefault(conversationId, List.of()));
    }

    @Override
    public synchronized void saveAll(String conversationId, List<Message> messages) {
        conversations.put(conversationId, List.copyOf(messages));
    }

    @Override
    public synchronized void deleteByConversationId(String conversationId) {
        conversations.remove(conversationId);
    }
}
