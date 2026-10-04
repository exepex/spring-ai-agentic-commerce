# ADR-0002: Agents handle exceptions; the happy path stays deterministic code

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0006, ADR-0007

## Context

An online shop has a high-volume normal flow (browse, order, pay, ship) and a long tail of exceptions: a customer
asking for help, a stock-out after payment, a lost parcel, a refund that failed at the processor, an incident the
service desk raises. The normal flow is rules. The exceptions need reading free text, weighing facts, choosing who
should act, and explaining the outcome to a customer.

## Decision

No AI agent sits on the checkout path. Browsing, ordering, payment, stock and shipping are plain Spring Boot code.

Agents are used only where judgement is needed:

- the **shopping assistant** chats with one signed-in customer, finds products, proposes orders, and cancels or refunds
  that customer's orders;
- the **incident agent** works every problem as a ServiceNow incident.

Deterministic work stays in code even inside exception handling. Which orders a stock-out affects is calculated by the
catalog, and cases are opened by code, not by a model's judgement.

## Options considered

| Option | Why not chosen |
|---|---|
| An agent orchestrates checkout | Adds seconds of latency and a model cost to every order, and makes the most important path unpredictable and hard to audit. |
| No agents; people handle every exception | Works, but every stock-out and lost parcel is manual work. This is the baseline the agents improve on. |
| Agents decide everything inside an exception, including which orders are affected | A model can be wrong about facts that code can compute exactly. |

## Consequences

- Checkout latency, cost and correctness don't depend on a model. The load test measures it without any model calls.
- Agent cost grows with the number of exceptions, not the number of orders.
- When the model is unavailable, the shop keeps selling; only exception handling falls back to people (ADR-0009).
- The boundary has to be kept: a new feature must justify why it needs judgement before it gets an agent.

## In the code

- `catalog-service`, `order-service`, `payment-service`, `shipping-service`: no model client.
- `agent-service`: `ShoppingAssistant`, `IncidentAgent`.
- `CaseService`: cases opened by code from events.

## In an interview

**Q: Why use agents at all here?** For the work that needs language and judgement: interpreting a customer's request
or a service-desk incident, choosing the right team, and explaining what happened. Everything that is a rule is code.

**Q: Why not put the agent on checkout? It could upsell.** Checkout must be fast, cheap and predictable. A model adds
latency and cost per order and behaviour you can't fully predict. The assistant can suggest; checkout stays code.

**Q: What does the agent decide that code couldn't?** What a free-text incident is about, which team should own it,
whether the facts support a refund under the policy, and how to tell the customer. It never decides which orders a
stock-out affected; the catalog computes that.
