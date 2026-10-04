# ADR-0018: Java and Spring AI for the agents

- **Status:** Accepted
- **Date:** 2026-10-04

## Context

The services are Spring Boot. Agents could be built in the same stack or in Python with an agent framework such as
LangGraph.

## Decision

Build agent-service and the MCP servers with Spring Boot and Spring AI. Spring AI provides the model client, chat
memory, and both the MCP client and the MCP server. The agent loop (tools, budget, decisions) is small and owned here.

## Options considered

| Option | Why not chosen |
|---|---|
| Python with LangGraph | A good fit for Python teams. Here it would add a second stack, with its own security, observability and deployment, next to an enterprise Java estate. |
| A hosted agent platform | Less control over identity, enforcement and audit, which are the point of the project. |

## Consequences

- Agents reuse the platform's security, tracing, configuration and deployment, and the team's skills.
- Spring AI moves fast; upgrades need care.

## In an interview

**Q: Why not Python?** The target estate is Java, so the agents reuse its platform. The patterns (an enforcement point,
idempotent tools, hand-over to people) carry across to any framework.
