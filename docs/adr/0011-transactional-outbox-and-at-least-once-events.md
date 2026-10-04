# ADR-0011: Transactional outbox and at-least-once events

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0008, ADR-0014

## Context

A service that commits a change and then publishes an event can crash in between, so the event is lost; or it can
publish first and then fail to commit, so the event announces something that never happened.

## Decision

- **Outbox.** A service writes each event into its own `outbox_event` table in the same transaction as the change.
- **Relay.** A relay in `commerce-platform` sends events to Kafka, oldest first, as soon as the transaction commits,
  and deletes each one Kafka took. Only one instance relays at a time (a Postgres advisory lock), so events leave in
  order.
- **Consumers are idempotent.** Delivery is at least once, so cases and audit entries are keyed by the event's id.
- **Poison events are parked.** A listener retries a few times with growing pauses, then sends the event to
  `<topic>.DLT` as it arrived, and the events behind it go on.
- **Traces travel with events.** Each event carries the trace it was raised in.

## Options considered

| Option | Why not chosen |
|---|---|
| Publish after commit, no outbox | Loses events on a crash. |
| Change data capture (Debezium) | Robust, but another platform to run; the outbox table is CDC-ready if needed. |
| Distributed transactions (XA) | Not supported by Kafka, and couples services' availability. |

## Consequences

- An event exists exactly when its change committed.
- Consumers may see an event twice and must handle that.
- Ordering holds per order or product, because their events share a partition (ADR-0014).

## In the code

- `OutboxRelay` (`commerce-platform`), `commerce.consumers`, `<topic>.DLT`

## In an interview

**Q: How do you avoid dual writes?** The transactional outbox: the event is part of the same database transaction, and
a relay publishes it afterwards.

**Q: What if an event can't be processed?** It is retried a few times, then parked on a dead-letter topic, so one bad
event doesn't stop the partition.
