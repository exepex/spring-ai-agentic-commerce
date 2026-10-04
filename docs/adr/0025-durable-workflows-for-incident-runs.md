# ADR-0025: Durable workflows for incident runs

- **Status:** Proposed
- **Date:** 2026-10-04
- **Supersedes when accepted:** ADR-0015

## Context (the limitation today)

An incident run is leased by two timeouts: a 15-minute run window and a 20-minute Kafka redelivery (ADR-0015). A run's
progress lives in the incident and the audit trail, so a redelivered run re-discovers what was done. A refund waiting
for approval ends the run, and the incident goes to a team.

## Proposed decision

Run each incident as a **durable workflow** (Temporal, or a comparable engine):

- Each tool call is an **activity** with its own retry policy and timeout.
- The model's turns are activities too, so a crash resumes from the last completed step instead of starting over.
- A refund waiting for approval becomes a **signal**: the workflow waits for the approval and then continues (notify,
  resolve) instead of handing the incident to a team.
- The workflow id is the incident number, so a second start for the same incident is rejected by the engine.

## Options

| Option | Trade-off |
|---|---|
| Keep the time-based lease | No new infrastructure; implicit coordination. |
| Temporal | Explicit state, retries and waiting; a cluster to run and a programming model to learn. |
| A state table plus scheduler | Lighter; reimplements what a workflow engine gives for free. |

## In an interview

**Q: What's the weakest part of the agent runtime?** Coordination by timeouts. I'd move incident runs to durable
workflows: explicit state, per-step retries, and approvals as signals instead of hand-offs.
