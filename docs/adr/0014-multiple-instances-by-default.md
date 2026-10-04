# ADR-0014: Every service runs as several instances

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0011, ADR-0015

## Context

A production system runs more than one instance of each service, for availability and load. Scheduled jobs, relays and
consumers that are correct on one instance can do the same work twice on two.

## Decision

- **Scheduled jobs** (pollers, reconcilers) take a ShedLock lease in Postgres, renewed while the job runs
  (`KeepAliveLockProvider`), so one instance runs each job at a time.
- **The outbox relay** runs on one instance at a time under a Postgres advisory lock, so events leave in order.
- **Kafka** topics have six partitions, and an order's or product's events share a partition, so they are handled in
  order. Each listener runs up to three consumers.
- **Chat memory** is in Postgres, so any instance can continue a conversation (ADR-0019).

## Consequences

- Adding an instance needs no code change.
- Scheduled work runs once per interval across all instances, not once per instance.

## In the code

- `SchedulerLockAutoConfiguration`, `OutboxRelay` (`commerce-platform`)
- `spring.kafka.listener.concurrency`

## In an interview

**Q: What breaks when you run two instances?** Typically duplicate scheduled jobs and out-of-order events. Here jobs
take a lease, the relay takes an advisory lock, and ordering comes from partition keys.
