package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.exepex.commerce.agent.dto.Agent;
import io.github.exepex.commerce.agent.dto.Agents;
import io.github.exepex.commerce.agent.dto.ServiceNow;
import io.github.exepex.commerce.agent.dto.Slack;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The shopping assistant's memory against this service's real schema, as two instances of the service share it. */
class ConversationMemoryTest {

    private static final String ANA = "ana@example.com";
    private static final String BEN = "ben@example.com";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static DriverManagerDataSource dataSource;

    @BeforeAll
    static void migrate() {
        POSTGRES.start();
        var url = POSTGRES.getJdbcUrl();
        dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=agents",
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).schemas("agents").defaultSchema("agents").load().migrate();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    void aConversationGoesOnWhicheverInstanceTheNextMessageReaches() {
        var first = instance(Instant.now());
        var second = instance(Instant.now());
        var key = ConversationMemory.key(ANA, "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c");

        first.memory().add(key, List.of(new UserMessage("Do you have trail shoes?"), new AssistantMessage("Yes.")));

        assertThat(second.memory().get(key)).extracting(Message::getText)
                .containsExactly("Do you have trail shoes?", "Yes.");
    }

    @Test
    void anotherCustomerSendingTheSameConversationIdStartsAConversationOfTheirOwn() {
        var memory = instance(Instant.now()).memory();
        var conversation = "0b7d5e1a-3c2f-4e8a-9b6d-2f1e0c9a8b7d";
        memory.add(ConversationMemory.key(ANA, conversation), List.of(new UserMessage("My address is 1 Main St.")));

        assertThat(memory.get(ConversationMemory.key(BEN, conversation))).isEmpty();
    }

    @Test
    void onlyTheLastMessagesOfAConversationAreKept() {
        var memory = instance(Instant.now()).memory();
        var key = ConversationMemory.key(ANA, "long-conversation");
        for (int i = 0; i < ConversationMemory.MAX_MESSAGES + 5; i++) {
            memory.add(key, List.of(new UserMessage("message " + i)));
        }

        assertThat(memory.get(key)).hasSize(ConversationMemory.MAX_MESSAGES)
                .first().extracting(Message::getText).isEqualTo("message 5");
    }

    @Test
    void aConversationNobodyWritesToForADayIsForgottenAndAnActiveOneIsKept() {
        var memory = instance(Instant.now()).memory();
        var idle = ConversationMemory.key(ANA, "idle-conversation");
        var active = ConversationMemory.key(BEN, "active-conversation");
        memory.add(idle, List.of(new UserMessage("Hello")));
        memory.add(active, List.of(new UserMessage("Hello")));
        JdbcClient.create(dataSource)
                .sql("update spring_ai_chat_memory set \"timestamp\" = \"timestamp\" - interval '25 hours'"
                        + " where conversation_id = :idle")
                .param("idle", idle)
                .update();

        instance(Instant.now()).forgetIdleConversations();

        assertThat(memory.get(idle)).isEmpty();
        assertThat(memory.get(active)).extracting(Message::getText).containsExactly("Hello");
    }

    /** One instance of the service: its own repository and memory over the shared database. */
    private static ConversationMemory instance(Instant now) {
        ChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
                .jdbcTemplate(new JdbcTemplate(dataSource))
                .dialect(new PostgresChatMemoryRepositoryDialect())
                .transactionManager(new DataSourceTransactionManager(dataSource))
                .build();
        var properties = new AgentProperties(new Agents("http://localhost:8085", new Agent("assistant-token"),
                new Agent("incident-token"), Duration.ofMinutes(15), Duration.ofDays(1)),
                new Slack("", "", ""), new ServiceNow(""));
        return new ConversationMemory(repository, JdbcClient.create(dataSource), properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
