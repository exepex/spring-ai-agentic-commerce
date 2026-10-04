# ADR-0010: Microservices, each with its own schema

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0011, ADR-0012

## Context

The project shows how to put governed agents onto the kind of estate enterprises already run: several services, each
owning its data and owned by a team. A small shop on its own wouldn't need that.

## Decision

Separate Spring Boot services for catalog, orders, payments and shipping, plus agent-service and the two MCP servers.
Each service owns its own Postgres schema (`catalog`, `governance`, `agents`, …) and announces changes as Kafka events.
No service reads another's tables. Agents never touch a database; they go through MCP tools, which call the services'
internal endpoints.

## Options considered

| Option | Why not chosen |
|---|---|
| A modular monolith | Simpler and cheaper for a small shop. It wouldn't show agents working against independent services, which is the point of the project. |
| A database per service on separate servers | Better isolation; more to run for a demo. The schema boundary keeps the option open. |

## Consequences

- Each service can scale, fail and be released on its own; several instances of each are supported.
- Consistency across services needs events, an outbox and reconcilers (ADR-0011, ADR-0012).
- Twelve containers is a lot to run and reason about.

## In an interview

**Q: Why microservices? Wouldn't a monolith be simpler?** Yes, for this shop alone. The goal is governed agents on an
enterprise estate, which is usually many services. Shipping and orders would be the natural merge if I simplified.

**Q: Why one Postgres with schemas?** Ownership is enforced by schema, and a service could move to its own database
without code changes. One instance keeps the demo light.
