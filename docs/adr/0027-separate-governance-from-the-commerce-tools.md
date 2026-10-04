# ADR-0027: Separate governance from the commerce tools

- **Status:** Proposed
- **Date:** 2026-10-04
- **Related:** ADR-0004, ADR-0022

## Context (the limitation today)

The commerce MCP server serves the shop's tools and also holds the governance API: kill switches, audit trail, cases,
refund requests and proposals. The ServiceNow MCP server and agent-service depend on it. A deploy or outage of the shop's
tools affects governance for every agent.

## Proposed decision

Move governance (kill switches, audit trail, cases, approvals) into its own **governance service**, or into the gateway
from ADR-0022. The commerce MCP server becomes a tool server like the others. `governance-api` stays as the contract,
so clients change only their base URL.

## Consequences

- Tool servers become interchangeable and independently deployable.
- One more service to run; refund approval must still be enforced where the money moves.

## In an interview

**Q: What would you split differently?** Governance out of the commerce MCP server. It's there because it grew with the
first server; it belongs to the platform, not to one domain.
