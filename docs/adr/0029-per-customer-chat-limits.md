# ADR-0029: Per-customer chat rate and token limits

- **Status:** Proposed
- **Date:** 2026-10-04
- **Related:** ADR-0024

## Context (the limitation today)

The assistant has a tool-call budget per run, but nothing limits how many messages or tokens one customer can use.
One abusive customer, or a script, could drive up model cost.

## Proposed decision

- Limit messages per customer per minute, and tokens per customer per day, at the chat endpoint (Bucket4j or the
  gateway's rate limiter, with state in Redis or Postgres so it holds across instances).
- Answer with a clear "try again in a minute" message, and record limit hits in the audit trail.
- Expose spend per agent and per customer from the token usage already recorded (ADR-0016).

## In an interview

**Q: How do you control agent cost?** Agents only run on exceptions; each run has a tool-call budget; effort is set per
agent; token usage is recorded per decision. The missing piece is per-customer limits on chat, which I'd add at the
endpoint or the gateway.
