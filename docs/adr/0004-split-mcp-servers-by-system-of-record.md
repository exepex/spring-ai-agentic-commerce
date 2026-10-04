# ADR-0004: Split MCP servers by system of record; share one governance pipeline

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0003, ADR-0022

## Context

The agents need tools over two of our systems (the shop and ServiceNow) and one third-party system (Slack). A large
organisation might expect one central MCP server. The question is what to centralise: the tools, or the rules.

## Decision

One MCP server per system of record:

| Server | Wraps | Built by |
|---|---|---|
| Commerce MCP server | Orders, refunds, notifications; also holds the governance API, cases, kill switches and the audit trail | Us |
| ServiceNow MCP server | Incidents: read, work note, list teams, assign, resolve | Us |
| Slack MCP server | Posting to one channel | Third party, used as is |

The governance is **not** split. Both of our servers run every call through the same pipeline from
`mcp-server-support`. The ServiceNow server reads the kill switches from the commerce server and writes its audit
entries there through `governance-api`. Both read the same agent definition files. There is one set of kill switches
and one audit trail.

## Options considered

| Option | Why not chosen |
|---|---|
| One MCP server for everything | Holds every system's credentials in one process (largest blast radius), couples every team's releases, and lets one slow dependency exhaust threads for all tools. |
| One server per system, each with its own rules | Rules would drift between servers, and there would be two kill switches and two audit trails to reconcile. |
| A gateway in front of domain servers | The right shape for a large organisation; more than this demo needs today. Proposed in ADR-0022. |

## Consequences

- **Least privilege:** the commerce server holds only the shop's internal token; the ServiceNow server holds only the
  ServiceNow login. Compromising one doesn't expose the other.
- **Ownership:** each server can be owned and released by the team that knows its system.
- **Isolation:** ServiceNow being slow or down never blocks refunds or the shopping assistant.
- **Cost:** a policy change means upgrading the shared module in every server. The commerce server is both a tool
  server and the governance store, so the ServiceNow server depends on it (ADR-0022 addresses both).

## In the code

- `mcp-server-support`, `governance-api`, `commerce-mcp-server`, `servicenow-mcp-server`.
- Slack: `commerce.slack` in agent-service's `application.yml`.

## In an interview

**Q: Why two MCP servers instead of one?** I split by system of record so each server holds only its own credentials,
is owned by the team that knows the system, and fails and scales on its own. I did not split the governance: every
call goes through one shared pipeline, one kill-switch store and one audit trail.

**Q: Wouldn't a bank build one central MCP platform?** It would centralise the control plane: one gateway for identity,
policy, audit and the kill switch, with domain MCP servers behind it. That's ADR-0022, and this design moves there
without changing the tools.

**Q: Does the number of servers affect keeping data inside the network?** No. That depends on where the model runs and
what tools return (ADR-0024).
