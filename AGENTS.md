# Instructions for AI agents and reviewers

This file is read by code-review agents (Codex) and coding agents (Claude Code). It says what this repository is,
which review findings are wanted, and how changes are made.

## What this repository is

A working demo of governed AI agents on ordinary Spring Boot microservices: an online shop, two agents, an MCP
server that enforces the rules, and an audit trail. The README describes the services and the workflows.

It is a demo, not a production system. Each service runs as one instance, and some production concerns are
deliberately left out (see "Design decisions" in the README). But it must be a **good** demo: every workflow in the
README has to work correctly, including the edge cases a real user, operator or model can cause.

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
