-- The shopping assistant's conversations, so any instance of agent-service can continue one and a restart loses none.
-- Spring AI's JdbcChatMemoryRepository reads and writes this table. Its key is longer than Spring AI's default
-- because it holds the customer as well as the conversation.
create table spring_ai_chat_memory (
    conversation_id varchar(320) not null,
    content         text         not null,
    type            varchar(10)  not null check (type in ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     timestamp    not null,
    sequence_id     bigint       not null
);

create index spring_ai_chat_memory_conversation on spring_ai_chat_memory (conversation_id, sequence_id);

-- Idle conversations are found through their old messages.
create index spring_ai_chat_memory_timestamp on spring_ai_chat_memory ("timestamp");
