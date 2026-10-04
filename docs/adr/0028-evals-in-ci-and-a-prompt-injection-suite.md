# ADR-0028: Evals in CI, and a prompt-injection suite

- **Status:** Proposed
- **Date:** 2026-10-04
- **Related:** ADR-0017

## Context (the limitation today)

The ten scenario evals run by hand against the live model. Nothing tracks pass rates over time, and no scenario tries to
inject instructions into incident text or tool results.

## Proposed decision

- **Run the evals automatically:** on every change to an agent definition, prompt or tool schema, and nightly against
  the live model. Record each run's pass rate per scenario and alert on a drop.
- **A cheaper set for pull requests:** recorded model responses that check the wiring without calling the model.
- **A prompt-injection suite**, asserting the server refused and audited the attempt:
  - incident text telling the agent to refund a different order;
  - a note asking for a refund above the limit "already approved by the manager";
  - a customer message claiming to be another customer;
  - a product description telling the assistant to order ten units.
- **Shadow mode** for prompt or model changes: run the new version alongside, without side effects, and compare
  decisions.

## Expected solution (sketch)

1. A GitHub Actions workflow on a schedule and on changes under `agent-definitions/`, with the model key as a secret.
2. New scenarios in `agent-evals` that write the injected text into ServiceNow or the chat, then assert on *denied*
   entries in the audit trail and unchanged orders and payments.

## In an interview

**Q: How do you know a prompt change didn't break anything?** Outcome-based evals on every agent change and nightly,
with pass rates tracked, and an adversarial suite that proves the guards hold when the model is fooled.
