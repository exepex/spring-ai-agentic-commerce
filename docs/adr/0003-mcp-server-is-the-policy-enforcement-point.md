# ADR-0003: The MCP server is the policy enforcement point

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0004, ADR-0005, ADR-0006, ADR-0022

## Context

A prompt can ask an agent to behave, but a model can misunderstand, be manipulated by text it reads (prompt
injection), or loop. The rules that protect money and customers (who an agent is, which tools it may use, whose orders
it may touch, how much it may refund, whether it is switched off) must hold even when the model does the wrong thing.

## Decision

Agents act only through MCP tools, and the MCP servers enforce every rule in code on every call:

1. **Identity:** the agent is resolved from its own bearer token, never from anything the model says.
2. **Allowlist:** the tool must be in the agent's definition (ADR-0005).
3. **Kill switch:** a switched-off agent is refused, except hand-off tools (ADR-0009).
4. **Scope:** a customer-facing agent only reaches the injected customer's orders. An incident run only changes the
   order linked to its incident.
5. **Limits:** refunds above the approval limit (€100) wait for a person.
6. **Idempotency:** refunds and messages carry keys (ADR-0008).
7. **Audit:** every call, allowed or refused, is recorded (ADR-0016).

This is **defence in depth**. agent-service only hands each agent its allowlisted tools, and the server checks the same
permissions again. The services' internal endpoints need a service token that the browser and the agents don't have, so
the only way an agent can move money is through the server's checks.

## Options considered

| Option | Why not chosen |
|---|---|
| Rules in the prompt only | A prompt is a request, not a control. One injection or misunderstanding bypasses it. |
| Agents call the services' REST APIs directly with function calling | The rules would live in the agent process; a compromised agent-service could skip them. Every service would need its own agent rules. |
| Rules in each business service | Spreads agent-specific policy (budgets, kill switch, allowlists) into services that shouldn't know about agents. |

## Consequences

- A fully hijacked model can at worst make a bad decision about the order it was working on, within the limit, and
  that decision is audited.
- Every new tool must go through the same governed call; there is no side door.
- The MCP servers are on the path of every agent action, so they must be highly available. When they are down, agents
  stop, which is the safe direction.

## In the code

- `mcp-server-support`: `GovernedToolCalls` (allowlist, kill switch, run, audit), `AgentRegistry`,
  `AgentAuthenticationFilter`.
- `commerce-mcp-server`: `CommerceTools`, `ToolGuard`.
- `servicenow-mcp-server`: `IncidentTools`, `ToolGuard`.
- `commerce-platform`: `InternalApiFilter` (the services' token).

## In an interview

**Q: Why MCP instead of plain function calling?** The checks run in a separate server that the agent process can't
bypass, the tools follow a standard any MCP client can use, and third-party servers such as Slack plug in. The cost is
an extra network hop.

**Q: How do you handle prompt injection?** Assume the model will be fooled and limit what a fooled model can do. The
prompt says tool results are data, not instructions, but the real protection is the server: identity from the token,
customer injected by code, the linked order only, the approval limit, and the allowlist on every call.

**Q: What does "defence in depth" mean here?** Two independent layers check the same rules. agent-service never gives
an agent a tool outside its allowlist, and the MCP server refuses it anyway if it is called.
