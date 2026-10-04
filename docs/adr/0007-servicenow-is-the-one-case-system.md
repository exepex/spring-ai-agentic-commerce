# ADR-0007: ServiceNow is the one case system; hand-offs go through it

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0009, ADR-0021

## Context

Problems with paid orders need an owner: a stock-out, a failed delivery, a lost parcel, a refund that failed at the
processor, or a customer the assistant couldn't help. Organisations already run this kind of work in an ITSM tool,
where people are trained, SLAs are tracked and teams are notified.

## Decision

- Every problem becomes a **case**, opened by code, and is worked as a **ServiceNow incident**.
- The **incident agent** works each incident first: it fixes what its policy allows, then resolves the incident or
  assigns it to a team.
- **Handing over to people** means assigning the incident to a team in ServiceNow; the shop keeps no queue of its own.
- **Agents hand over to each other through ServiceNow too.** When the shopping assistant can't finish, it calls
  `escalate_to_human`, which opens a `HANDOFF` case that becomes an incident for the incident agent.
- **Refund approvals are the exception.** They stay in the shop's operations console, because the approval limit is
  the MCP server's rule and has to be enforced where the money moves.

## Options considered

| Option | Why not chosen |
|---|---|
| A work queue inside the shop | A second place for people to work, outside the organisation's ITSM process. |
| Agents call each other directly | A failed call loses the work; the hand-off isn't durable, owned, audited or visible to people. |
| Agents open incidents themselves | Whether a problem exists is a fact code can determine; a model could skip or duplicate it. |

## Consequences

- People work where they already work, and every hand-off is durable and owned.
- Each agent keeps its own identity and permissions; the assistant's limited rights never mix with the incident
  agent's.
- The shop depends on ServiceNow for case handling; when it is unreachable, cases wait as *pending* and are sent later.

## In the code

- `CaseService` (opens cases), `CaseType` (`STOCK_OUT`, `DELIVERY_FAILED`, `PARCEL_LOST`, `REFUND_FAILED`, `HANDOFF`,
  `SERVICE_DESK`)
- `CaseSync`, `IncidentPoller`, `IncidentTools` (ServiceNow MCP server)

## In an interview

**Q: Why ServiceNow and not your own queue?** Because the organisation already runs operations there. The agent acts as
a first-line worker in the same tool, and handing over is just assigning to a team.

**Q: How do your two agents collaborate?** Through the case system, not by calling each other. A hand-off is an incident
with an owner, an audit trail and visibility for people.
