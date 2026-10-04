# ADR-0022: An MCP gateway with policy as code

- **Status:** Proposed
- **Date:** 2026-10-04
- **Related:** ADR-0003, ADR-0004, ADR-0023

## Context (the limitation today)

- Governance is a shared library (`mcp-server-support`) that every MCP server runs. That's consistent, but a policy
  change means upgrading and releasing every server.
- The commerce MCP server is both a tool server and the governance store (kill switches, audit trail, cases). The
  ServiceNow server depends on it.
- Agents connect to each server separately. A large organisation wants one controlled entry point, one registry of
  approved servers and tools, and central audit.

## Proposed decision

Put an **MCP gateway** in front of the domain MCP servers, owned by a platform team:

- **One endpoint for agents.** The gateway routes each tool call to the server that owns it.
- **Identity:** validates the agent's short-lived token from the organisation's identity provider (ADR-0023).
- **Policy as code:** allowlists, kill switches, rate limits and approval rules written in OPA (Rego) or Cedar and
  evaluated at the gateway, so they change without redeploying servers.
- **Audit:** every call is recorded and shipped to an append-only store (ADR-0026).
- **Registry:** which MCP servers and tools are approved, with an owner for each.

Domain servers keep only the checks that need domain data, such as "this order belongs to this customer". Governance
moves out of the commerce MCP server into the gateway, or into its own service behind it.

## Options

| Option | Trade-off |
|---|---|
| Keep the shared library | Simplest; policy changes need every server released. Fine for a few servers and one team. |
| Gateway with policy as code | One control point and faster policy changes; a new critical component that must be highly available. |
| Sidecar per server (service-mesh style) | Central policy, distributed enforcement, no single hop; more infrastructure. |

## Expected solution (sketch)

1. Stand up a gateway (off-the-shelf MCP gateway, or a thin Spring Cloud Gateway service that speaks MCP's HTTP
   transport).
2. Move the allowlist, kill switch and audit checks from `GovernedToolCalls` into gateway policy. Keep
   `ToolGuard`'s domain checks in the servers.
3. Point agent-service at the gateway only. Block direct access to the domain servers from anything else.
4. Run old and new paths side by side and compare decisions before switching.

## Open questions

- Build or buy the gateway?
- Latency budget for the extra hop on chat.

## In an interview

**Q: Would you have one MCP server for the whole organisation?** One *entry point*, yes: a gateway that owns identity,
policy, audit and the kill switch. The tools stay in domain servers owned by the teams that know each system.
