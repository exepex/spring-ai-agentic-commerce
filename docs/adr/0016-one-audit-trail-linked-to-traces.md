# ADR-0016: One audit trail, linked to traces

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0003, ADR-0020, ADR-0026

## Context

When an agent acts on money or customers, people need to know who did what, why, and how it turned out. Engineers need
to follow one action across services.

## Decision

- Every tool call, every agent decision (with model and token usage), every human decision and every system event is
  recorded in **one audit trail**. Each entry has its actor, outcome (succeeded, denied, pending approval, rejected,
  failed) and reason.
- Each entry links to its **OpenTelemetry trace** in Jaeger. Events carry the trace context, so a stock-out can be
  followed from the catalog through Kafka, both MCP servers and agent-service.
- The incident agent also writes **work notes** in ServiceNow, where people work.

## Consequences

- Refused attempts are visible, which is how prompt injections and misbehaviour show up.
- Token usage per decision allows cost per agent and per incident.
- The trail is a Postgres table, so an administrator could edit it (ADR-0026).

## In the code

- `AuditTrail` (commerce MCP server), `DecisionRecorder` (agent-service)

## In an interview

**Q: How do you debug an agent's decision?** Open the order's timeline. Each step shows who acted, the outcome and the
reason, and links to the trace for every service call behind it.
