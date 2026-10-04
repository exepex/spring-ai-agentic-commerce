# ADR-0021: Integrate with ServiceNow by batched polling of its Table API

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0007

## Context

ServiceNow is an external SaaS with rate limits. It can't push events into a private network without extra setup, and
its Table API has no conditional update.

## Decision

- The ServiceNow MCP server **creates** an incident for each case, with the case id in its correlation field, so a
  retry finds the incident instead of opening a second one.
- It **polls** the agent's assignment group and reads incidents back in batches. Claimed incidents are published to
  Kafka for agent-service.
- It **reads an incident again** right before claiming or handing it over.
- The agent's tools only work on incidents assigned to its integration user, so once a person takes an incident, the
  agent stops.

## Consequences

- Works with any ServiceNow instance, with no inbound connection.
- Updates arrive with polling delay.
- Known race: a person who takes an incident between the re-read and the update is overwritten, because the Table API
  has no compare-and-set. The fix is a scripted REST endpoint in ServiceNow that updates only if the assignment is
  unchanged.

## In the code

- `CaseSync`, `IncidentPoller`, `ServiceNowClient`, `TableApiRows`

## In an interview

**Q: Why polling rather than webhooks?** It needs no inbound access to the network and survives outages. ServiceNow
business rules with an outbound REST call would cut latency once a route in exists.

**Q: Do you know your race conditions?** Yes: the Table API can't do a conditional update, so there is a small window.
It is documented, with the fix.
