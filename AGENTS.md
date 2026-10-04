# Instructions for AI agents and reviewers

This file is the one place for everything AI agents follow in this repository. Code-review agents (Codex) and coding
agents (Claude Code, through `CLAUDE.md`) read it. It says what this repository is, where the demo's own agents are
defined, which review findings are wanted, and how changes are made.

## What this repository is

A working demo of governed AI agents on ordinary Spring Boot microservices: an online shop, two agents, an MCP
server that enforces the rules, and an audit trail. The README describes the services and the workflows.

It is a demo built to production standards. Every service may run as several instances (see "Operate" in the
README), and some production concerns are deliberately left out (see "Design decisions" in the
README, and the open issues). It must work correctly: every workflow in the README, including the edge cases a real
user, operator or model can cause, and at volume.

## The demo's agents

Each agent the demo runs is defined in **one file**. The YAML header holds the agent's model, effort, tool-call
budget, whether it is customer-scoped, and its tools on each MCP server. The Markdown body is its prompt.
agent-service (which runs the agent) and the MCP servers (which enforce its permissions) read the same file.

| Agent | Definition | What it does |
|---|---|---|
| `shopping-assistant` | [shopping-assistant.md](agent-definitions/src/main/resources/agents/shopping-assistant.md) | Chats with one signed-in customer: finds products, proposes orders, cancels and refunds their orders. |
| `incident-agent` | [incident-agent.md](agent-definitions/src/main/resources/agents/incident-agent.md) | Works every case first, as a ServiceNow incident (stock-outs, failed deliveries, lost parcels, failed refunds, hand-offs, and incidents the service desk raises): gathers the facts, cancels, refunds and tells the customer where its policy allows, then resolves the incident or hands it to the right team. |

Everything else an agent depends on has one home too:

| What | Where |
|---|---|
| Each agent's bearer token | Environment variables (see `.env.example`), referenced in the `application.yml` of agent-service and each MCP server |
| The refund approval limit | `commerce.governance.refund-approval-threshold` in [commerce-mcp-server/src/main/resources/application.yml](commerce-mcp-server/src/main/resources/application.yml) |
| How every MCP tool call is governed, on both servers | [GovernedToolCalls](mcp-server-support/src/main/java/io/github/exepex/commerce/mcpserver/guard/GovernedToolCalls.java) (allowlist, kill switch, run, audit), [AgentRegistry](mcp-server-support/src/main/java/io/github/exepex/commerce/mcpserver/security/AgentRegistry.java) and [AgentAuthenticationFilter](mcp-server-support/src/main/java/io/github/exepex/commerce/mcpserver/security/AgentAuthenticationFilter.java), in the shared `mcp-server-support` module |
| The commerce MCP tools and their rules | [CommerceTools](commerce-mcp-server/src/main/java/io/github/exepex/commerce/mcp/tools/CommerceTools.java) and [ToolGuard](commerce-mcp-server/src/main/java/io/github/exepex/commerce/mcp/tools/ToolGuard.java) (this server's kill switch, refusals, audit entries and customer scoping) |
| The Slack MCP server and its channel | `commerce.slack` in [agent-service/src/main/resources/application.yml](agent-service/src/main/resources/application.yml) |
| The ServiceNow instance, the agent's group and the teams | `commerce.servicenow` in [servicenow-mcp-server/src/main/resources/application.yml](servicenow-mcp-server/src/main/resources/application.yml) |
| The ServiceNow tools and their rules | [IncidentTools](servicenow-mcp-server/src/main/java/io/github/exepex/commerce/servicenow/incidents/IncidentTools.java) and [ToolGuard](servicenow-mcp-server/src/main/java/io/github/exepex/commerce/servicenow/governance/ToolGuard.java); ServiceNow itself sits behind [IncidentSystem](servicenow-mcp-server/src/main/java/io/github/exepex/commerce/servicenow/incidents/IncidentSystem.java) |
| The agent governance API (audit trail, decisions, kill switches, cases) | Its contract and clients in the shared `governance-api` module ([GovernancePaths](governance-api/src/main/java/io/github/exepex/commerce/governance/api/GovernancePaths.java)) |
| What every service shares: error answers, event publishing (transactional outbox), the internal API token, log safety, bearer tokens, clock, tracing defaults | The shared `commerce-platform` module ([OutboxRelay](commerce-platform/src/main/java/io/github/exepex/commerce/platform/events/OutboxRelay.java), [InternalApiFilter](commerce-platform/src/main/java/io/github/exepex/commerce/platform/security/InternalApiFilter.java), [CommerceException](commerce-platform/src/main/java/io/github/exepex/commerce/platform/error/CommerceException.java), [ProblemDetailsExceptionHandler](commerce-platform/src/main/java/io/github/exepex/commerce/platform/error/ProblemDetailsExceptionHandler.java)) |
| The cases, and how they reach ServiceNow and come back | [CaseService](commerce-mcp-server/src/main/java/io/github/exepex/commerce/mcp/cases/CaseService.java) and [CaseSync](servicenow-mcp-server/src/main/java/io/github/exepex/commerce/servicenow/incidents/CaseSync.java) |
| The kill switches | Kept and enforced by [AgentSwitches](commerce-mcp-server/src/main/java/io/github/exepex/commerce/mcp/governance/AgentSwitches.java); agent-service reads them through [AgentSwitchboard](agent-service/src/main/java/io/github/exepex/commerce/agent/AgentSwitchboard.java) |

To change an agent's model, effort, tools, budget or prompt, edit its definition file; nothing else needs to change.
A tool listed there must exist on its MCP server. Adding a new agent also needs code in agent-service that runs it,
and a token for it in the configuration of agent-service and of each MCP server it uses. They refuse to start if a
definition or a token is missing.

## Review guidelines

Report a finding when it describes a concrete scenario that this code can actually reach, and the result is one of:

- **A README workflow behaves wrongly**, including under realistic edge cases:
  - a double click or a retried request;
  - two people acting on the same item at the same time (a refund, a case, a proposal);
  - a dependency that is down or slow (payments, the MCP server, Slack, Claude, Kafka, the database);
  - load: a query, lock or pool that stops keeping up as orders, cases or events grow;
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
- **Trade-offs already decided.** These are listed under "Design decisions" in the README.
- **Concerns that only apply to a single instance.** Several instances of every service are in scope: report what two
  instances doing the same work at once would break.
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
