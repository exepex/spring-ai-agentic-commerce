# Instructions for AI agents and reviewers

This file is the one place for everything AI agents follow in this repository. Code-review agents (Codex) and coding
agents (Claude Code, through `CLAUDE.md`) read it. It says what this repository is, where the demo's own agents are
defined, which review findings are wanted, and how changes are made.

## What this repository is

A working demo of governed AI agents on ordinary Spring Boot microservices: an online shop, two agents, an MCP
server that enforces the rules, and an audit trail. The README describes the services and the workflows.

It is a demo, not a production system. Each service runs as one instance, and some production concerns are
deliberately left out (see "Design decisions" in the README). But it must be a **good** demo: every workflow in the
README has to work correctly, including the edge cases a real user, operator or model can cause.

## The demo's agents

Each agent the demo runs is defined in **one file**. The YAML header holds the agent's model, effort, tool-call
budget, whether it is customer-scoped, and its tools on each MCP server. The Markdown body is its prompt. Both
agent-service (which runs the agent) and commerce-mcp-server (which enforces its permissions) read the same file.

| Agent | Definition | What it does |
|---|---|---|
| `shopping-assistant` | [shopping-assistant.md](agent-definitions/src/main/resources/agents/shopping-assistant.md) | Chats with one signed-in customer: finds products, proposes orders, cancels and refunds their orders. |
| `order-exceptions-agent` | [order-exceptions-agent.md](agent-definitions/src/main/resources/agents/order-exceptions-agent.md) | Handles a stock-out on a paid order: cancels, refunds, tells the customer, posts to Slack. |

Everything else an agent depends on has one home too:

| What | Where |
|---|---|
| Each agent's bearer token | Environment variables (see `.env.example`), referenced in both services' `application.yml` |
| The refund approval limit | `commerce.governance.refund-approval-threshold` in [commerce-mcp-server/src/main/resources/application.yml](commerce-mcp-server/src/main/resources/application.yml) |
| The MCP tools and how each rule is enforced | [CommerceTools](commerce-mcp-server/src/main/java/io/github/exepex/commerce/mcp/tools/CommerceTools.java), [ToolGuard](commerce-mcp-server/src/main/java/io/github/exepex/commerce/mcp/tools/ToolGuard.java) and [AgentRegistry](commerce-mcp-server/src/main/java/io/github/exepex/commerce/mcp/security/AgentRegistry.java) |
| The Slack MCP server and its channel | `commerce.slack` in [agent-service/src/main/resources/application.yml](agent-service/src/main/resources/application.yml) |
| The kill switches | [AgentSwitchboard](agent-service/src/main/java/io/github/exepex/commerce/agent/AgentSwitchboard.java) |

To change an agent's model, effort, tools, budget or prompt, edit its definition file; nothing else needs to change.
A tool listed there must exist on its MCP server. Adding a new agent also needs code in agent-service that runs it,
and a token for it in both services' configuration. Both services refuse to start if a definition or a token is
missing.

## Review guidelines

Report a finding when it describes a concrete scenario that this code can actually reach, and the result is one of:

- **A README workflow behaves wrongly**, including under realistic edge cases:
  - a double click or a retried request;
  - two people acting on the same item at the same time (a refund, an escalation, a proposal);
  - a dependency that is down or slow (payments, the MCP server, Slack, Claude);
  - the model misbehaving: calling the wrong tool, stopping without acting, or obeying a prompt injection.
- **Money or stock is wrong:** a double charge or refund, a refund above what was paid, or stock oversold or never
  released.
- **A governance rule can be bypassed:**
  - the refund approval limit;
  - customer scoping;
  - an agent's tool allowlist;
  - the kill switch;
  - an action that leaves no audit entry.
- **A security problem:** a secret exposed, or one agent able to act with another agent's identity.
- **A broken contract** between two services, or between the UI and a service.

State the steps that trigger the problem. Then say what goes wrong.

Do not report:

- **Scenarios that need a code path that does not exist.** For example, products come only from the seed migration
  and there is no API to create them. Hand-edited database rows are out of scope too.
- **Items already tracked.** These are the open issue
  [#4](https://github.com/exepex/spring-ai-agentic-commerce/issues/4) (recovery from crashes, timeouts and
  redelivery) and the trade-offs listed under "Design decisions" in the README.
- **Concerns that only apply to running several instances of a service.**
- **Style, naming or formatting** without a functional effect.

## Working rules for coding agents

- Read the code you change and its tests first. Keep each change to what the task or finding needs.
- `mvn verify` must pass. For the UI, the production build must pass: `npm run build` in `shop-ui`.
- A bug fix comes with a test that fails without the fix and passes with it.
- Before pushing a fix, check that the new code does not open a new edge case of its own.
- Answer every review finding on its thread: fixed (with the commit), moved to an issue (with the link and the
  reason), or declined (with the evidence). Do not argue a declined finding again unless the reviewer brings new
  evidence.
- At most two rounds of review fixes per pull request. Whatever is still open after that goes to an issue, so a pull
  request never becomes an endless back and forth.
- Never commit or print secrets. API keys, tokens and passwords come from environment variables; see `.env.example`.
