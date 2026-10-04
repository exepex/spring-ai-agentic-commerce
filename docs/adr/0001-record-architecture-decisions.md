# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-04

## Context

The system's design choices were described in the README's "Design decisions" list, as one line each. That says
*what* was decided, not *why*, which options were weighed, or what the choice costs. Anyone reviewing, extending or
defending the design needs the reasoning, and the open limitations need a place where their expected solution is
written down before anyone builds it.

## Decision

Keep architecture decision records (ADRs) in `docs/adr/`, one decision per file, numbered in order.

- **Accepted:** the system works this way today.
- **Proposed:** a known limitation, with the expected solution. Not built yet.
- **Superseded by ADR-NNNN:** replaced by a later decision. The old record is kept, not edited away.

Each record has: Context, Decision, Options considered, Consequences, where it lives in the code, and the questions a
reviewer or interviewer is likely to ask, with answers.

The decisions up to ADR-0021 were made while the system was built and were recorded afterwards, on the date shown.

## Consequences

- A new decision that changes how services, agents or governance work comes with an ADR in the same pull request.
- The README's "Design decisions" list stays as the short summary, and points here for the reasoning.

## In an interview

**Q: How do you document architecture?** With short decision records next to the code, reviewed in the same pull
request as the change. Each says what was decided, what else was considered and what it costs. Open limitations are
recorded as *Proposed* with their expected fix, so known debt is visible rather than tribal knowledge.
