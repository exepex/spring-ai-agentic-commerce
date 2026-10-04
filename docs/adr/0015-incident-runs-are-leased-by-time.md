# ADR-0015: Incident runs are leased by time

- **Status:** Accepted (to be replaced by ADR-0025)
- **Date:** 2026-10-04
- **Related:** ADR-0008, ADR-0009, ADR-0025

## Context

Incidents reach agent-service through Kafka. Two instances must never work the same incident at the same time, and an
incident whose run died must be picked up again.

## Decision

- The incident listener takes one incident per poll.
- A run may act for **15 minutes**. After that its tool calls are refused and the unfinished incident goes to a team.
- Kafka gives the incident to another consumer only after **20 minutes**, so two runs never overlap.
- A redelivered incident is worked again, re-entrantly (ADR-0008).

## Consequences

- Simple, with no extra infrastructure.
- The lease is implicit in two timeouts that must stay in the right order.
- A run's progress lives in the incident's notes and the audit trail, not in an explicit workflow state.

## In an interview

**Q: How do you stop two instances working one incident?** A time-bounded lease: the run's window is shorter than
Kafka's redelivery, and the tools refuse calls after the window.

**Q: What would you change?** Use a durable workflow engine, where each tool step is an activity and approvals are
signals (ADR-0025).
