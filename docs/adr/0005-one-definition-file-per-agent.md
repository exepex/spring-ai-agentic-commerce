# ADR-0005: One definition file per agent

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0003

## Context

An agent's model, effort, tool-call budget, scope, tools and prompt are read by two kinds of process: agent-service,
which runs the agent, and the MCP servers, which enforce its permissions. If each kept its own copy, the allowlist the
runtime offers and the allowlist the server enforces could drift apart.

## Decision

Each agent is defined in exactly one file in `agent-definitions`.

- The **YAML header** holds the id, model, effort, tool-call budget, whether the agent is customer-scoped, and its
  tools per MCP server.
- The **Markdown body** is its prompt.

agent-service and every MCP server read the same file. A service refuses to start if a definition or an agent's token
is missing, or if a listed tool doesn't exist on its server.

## Options considered

| Option | Why not chosen |
|---|---|
| Configuration in each service's `application.yml` | Two or three copies of the allowlist that must be kept in sync by hand. |
| A central agent registry service | Right for many agents and teams; a runtime dependency and more to operate for two agents. A gateway's tool registry can take this role later (ADR-0022). |
| Prompts in code | Prompt changes would need Java changes and would be hard to review. |

## Consequences

- Changing an agent's model, budget, tools or prompt is one reviewed file change.
- The definition is versioned with the code, so a rollback restores the matching prompt and permissions.
- A new agent still needs code in agent-service to run it, and a token configured for it.

## In the code

- `agent-definitions/src/main/resources/agents/shopping-assistant.md`, `incident-agent.md`
- `AgentRegistry` (MCP servers), `AgentProperties` (agent-service)

## In an interview

**Q: How do you change an agent safely?** Edit its definition in a pull request. The scenario evals run against the
change before release. The runtime and the enforcement read the same file, so they can't disagree.

**Q: How would this scale to fifty agents across teams?** Move the definitions into a registry that the gateway reads,
with an owning team per agent and promotion between environments. The file format stays the same.
