# ADR-0008: Agent work is idempotent and re-entrant

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0011, ADR-0015

## Context

Events are delivered at least once, runs can crash halfway, and an incident can reach the agent again after an
interruption. An agent that simply repeats its steps would refund twice or message the customer twice.

## Decision

- **Keys for side effects.** The incident agent's refund uses the key `refund-<order id>-<incident number>`; the payment
  service pays out once per key. The message to the customer carries a key made of the order and the incident, and that
  key is set by **code**, not the model.
- **Tools expose what is already done.** `get_order` returns each refund's key and status, and when the customer was
  notified.
- **The prompt is re-entrant.** The incident agent first finds out which steps are already done, and does only the
  others.
- **Confirming a proposal is idempotent.** The order is placed under the proposal's id.

## Options considered

| Option | Why not chosen |
|---|---|
| Rely on the model to remember what it did | A new run has no memory of a crashed one; the facts must come from the system. |
| Exactly-once delivery | Not achievable end to end across Kafka, HTTP and ServiceNow; idempotency is the practical answer. |
| Let the model choose the message key | A model could vary the key and send twice. |

## Consequences

- A refund pays out once and the customer is told once, however often the incident is worked.
- Known gap: nothing records whether the Slack line was posted, so it may be posted twice. It is never missed.

## In the code

- `RefundService`, `NotificationService` (commerce MCP server); refund idempotency in `payment-service`.
- `incident-agent.md`, step 4 (the refund key) and step 5 (notify once).

## In an interview

**Q: What happens if the same incident is delivered twice?** The agent checks what's done through `get_order` and only
does the rest. If it tried to refund again, the key would make it a no-op at the payment service.

**Q: Which keys does code set and which does the model use?** The message key is set by code. The refund key's format is
given in the prompt and checked by the payment service, so a different key would show up as a second refund request in
the audit trail and against the refundable amount.
