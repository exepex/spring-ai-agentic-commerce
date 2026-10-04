# ADR-0019: Chat memory in Postgres, tied to the customer

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0006, ADR-0014

## Context

The assistant needs the conversation so far. With several instances, memory in one process breaks when the next
message lands on another instance. A conversation id alone could be reused to read someone else's conversation.

## Decision

Store chat memory in Postgres through Spring AI's JDBC chat memory, and tie each conversation to its customer, so a
conversation id can't be used to read another customer's history. Memory covers the conversation only. There is no
long-term profile and no retrieval over documents; the catalog is small enough to search with a tool.

## Consequences

- Any instance can continue a conversation.
- No document retrieval (RAG) is needed at this size; a large catalog or policy library would add it.

## In the code

- `ConversationMemory` (agent-service)

## In an interview

**Q: Where does the agent's memory live?** In Postgres, scoped to the customer, so it survives restarts and works
across instances without leaking between customers.
