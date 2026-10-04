# ADR-0017: Test the guards deterministically and the agents by outcome

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0028

## Context

The guards (identity, allowlists, scoping, limits, idempotency, audit) are deterministic code. The agents' behaviour
isn't. Asserting on a model's wording makes tests flaky and says little about what matters.

## Decision

- **The guards** are tested like any code: integration tests through a real MCP client, against real Postgres and Kafka
  (Testcontainers). Service-to-service calls are tested over real HTTP with WireMock.
- **The agents** are tested by ten scenario evals in `agent-evals`, against the full running system with the real
  model. They assert on what the agents *did*: the audit trail, order and payment state, and the ServiceNow incident's
  state and team, never on reply text.
- The scenarios cover stock-outs below and above the limit, a stock-out delivered twice, payments down, the kill switch,
  the shopping assistant, a failed delivery, a lost parcel, and a service-desk incident. Each puts back the stock it
  uses, so the suite can run again on the same database.
- A **k6 load test** checks the hot paths against thresholds.

## Consequences

- A prompt or model change can be checked against real outcomes.
- The evals call the model, so they cost money and run by hand today (ADR-0028).

## In the code

- `agent-evals` (`IncidentScenarioEvals`, `AgentBehaviourEvals`), `run-scenarios.sh`, `load-tests/shop.js`

## In an interview

**Q: How do you test something non-deterministic?** Split it. The rules are deterministic, so I test them exactly. For
behaviour, I assert on outcomes in the system of record, which are stable even when the wording isn't.
