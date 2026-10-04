package io.github.exepex.commerce.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.github.exepex.commerce.agent.dto.Agent;
import io.github.exepex.commerce.agent.dto.Agents;
import io.github.exepex.commerce.agent.dto.ServiceNow;
import io.github.exepex.commerce.agent.dto.Slack;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

/** The shopping assistant's memory against this service's real schema, as several instances of the service share it. */
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
        var key = ConversationMemory.key(ANA, "6f0c2b8e-1d4a-4f3b-9c2e-7a5d8e9f0b1c");

        instance(dataSource).add(key, List.of(new UserMessage("Do you have trail shoes?"), new AssistantMessage("Yes.")));

        assertThat(instance(dataSource).get(key)).extracting(Message::getText)
                .containsExactly("Do you have trail shoes?", "Yes.");
    }

    @Test
    void anotherCustomerSendingTheSameConversationIdStartsAConversationOfTheirOwn() {
        var memory = instance(dataSource);
        var conversation = "0b7d5e1a-3c2f-4e8a-9b6d-2f1e0c9a8b7d";
        memory.add(ConversationMemory.key(ANA, conversation), List.of(new UserMessage("My address is 1 Main St.")));

        assertThat(memory.get(ConversationMemory.key(BEN, conversation))).isEmpty();
    }

    @Test
    void onlyTheLastMessagesOfAConversationAreKept() {
        var memory = instance(dataSource);
        var key = ConversationMemory.key(ANA, "long-conversation");
        for (int i = 0; i < ConversationMemory.MAX_MESSAGES + 5; i++) {
            memory.add(key, List.of(new UserMessage("message " + i)));
        }

        assertThat(memory.get(key)).hasSize(ConversationMemory.MAX_MESSAGES)
                .first().extracting(Message::getText).isEqualTo("message 5");
    }

    @Test
    void turnsOfOneConversationOnTwoInstancesAtOnceNeitherLoseNorRepeatAMessage() throws Exception {
        var key = ConversationMemory.key(ANA, "busy-conversation");
        var instances = List.of(instance(dataSource), instance(dataSource));
        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            for (int t = 0; t < 2; t++) {
                var memory = instances.get(t);
                var thread = t;
                threads.submit(() -> {
                    start.await();
                    for (int i = 0; i < 10; i++) {
                        memory.add(key, List.of(new UserMessage(thread + "-" + i)));
                    }
                    return null;
                });
            }
            start.countDown();
        }

        assertThat(instances.getFirst().get(key)).extracting(Message::getText).containsExactlyInAnyOrderElementsOf(
                IntStream.range(0, 2).boxed()
                        .flatMap(thread -> IntStream.range(0, 10).mapToObj(i -> thread + "-" + i)).toList());
    }

    @Test
    void aConversationNobodyWritesToForADayIsForgottenAndAnActiveOneIsKeptWhole() {
        var memory = instance(dataSource);
        var idle = ConversationMemory.key(ANA, "idle-conversation");
        var active = ConversationMemory.key(BEN, "active-conversation");
        memory.add(idle, List.of(new UserMessage("Hello")));
        memory.add(active, List.of(new UserMessage("Hello")));
        ageMessages(idle);
        ageMessages(active);
        memory.add(active, List.of(new UserMessage("Still there?")));

        memory.forgetIdleConversations();

        assertThat(memory.get(idle)).isEmpty();
        assertThat(memory.get(active)).extracting(Message::getText).containsExactly("Hello", "Still there?");
    }

    @Test
    void aTurnIsStillAnsweredWhenItsMessagesCannotBeSaved() {
        var unreachable = new DriverManagerDataSource("jdbc:postgresql://localhost:1/commerce", "commerce", "none");

        assertThatNoException().isThrownBy(() -> instance(unreachable)
                .add(ConversationMemory.key(ANA, "lost-conversation"), List.of(new AssistantMessage("Refunded."))));
    }

    /** Makes the conversation's messages so far older than a day. */
    private static void ageMessages(String conversationId) {
        JdbcClient.create(dataSource)
                .sql("update spring_ai_chat_memory set \"timestamp\" = \"timestamp\" - interval '25 hours'"
                        + " where conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
    }

    /** One instance of the service: its own repository and memory over the database. */
    private static ConversationMemory instance(DataSource database) {
        var transactions = new DataSourceTransactionManager(database);
        var repository = JdbcChatMemoryRepository.builder()
                .jdbcTemplate(new JdbcTemplate(database))
                .dialect(new PostgresChatMemoryRepositoryDialect())
                .transactionManager(transactions)
                .build();
        var properties = new AgentProperties(new Agents("http://localhost:8085", new Agent("assistant-token"),
                new Agent("incident-token"), Duration.ofMinutes(15), Duration.ofDays(1)),
                new Slack("", "", ""), new ServiceNow(""));
        return new ConversationMemory(repository, JdbcClient.create(database), transactions, properties,
                Clock.fixed(Instant.now(), ZoneOffset.UTC));
    }
}
