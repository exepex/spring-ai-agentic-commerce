# Architecture decisions

Why the system is built the way it is, what else was considered, what each choice costs, and the open limitations
with their expected solutions. Every record ends with the questions a reviewer or interviewer is likely to ask, and
answers.

## How to read this folder

1. Start with the [interview guide](interview-guide.md): the pitch, the system map and the most likely questions.
2. Read the **accepted** decisions for the reasoning behind each answer.
3. Read the **proposed** decisions for the limitations and how you would fix them. Raise two or three of these yourself
   in an interview, each with its fix.

**Status:** *Accepted* means the system works this way today. *Proposed* means a known limitation with its expected
solution, not built yet. Decisions up to ADR-0021 were recorded after they were made (see ADR-0001).

## Accepted

| ADR | Decision | Topic |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Process |
| [0002](0002-agents-handle-exceptions-not-the-happy-path.md) | Agents handle exceptions; the happy path stays deterministic code | Solution design |
| [0003](0003-mcp-server-is-the-policy-enforcement-point.md) | The MCP server is the policy enforcement point | Governance |
| [0004](0004-split-mcp-servers-by-system-of-record.md) | Split MCP servers by system of record; share one governance pipeline | Integration |
| [0005](0005-one-definition-file-per-agent.md) | One definition file per agent | Agent engineering |
| [0006](0006-identity-injected-by-code-agents-propose-customers-confirm.md) | Identity is injected by code; agents propose, customers confirm | Governance |
| [0007](0007-servicenow-is-the-one-case-system.md) | ServiceNow is the one case system; hand-offs go through it | Solution design |
| [0008](0008-idempotent-re-entrant-agent-work.md) | Agent work is idempotent and re-entrant | Agent engineering |
| [0009](0009-fail-safe-toward-a-human.md) | Fail safe, toward a human | Reliability |
| [0010](0010-microservices-with-a-schema-per-service.md) | Microservices, each with its own schema | System design |
| [0011](0011-transactional-outbox-and-at-least-once-events.md) | Transactional outbox and at-least-once events | System design |
| [0012](0012-no-distributed-transactions-reconcile-instead.md) | No distributed transactions; finish unfinished work later | System design |
| [0013](0013-stock-changes-lock-the-product-row.md) | Stock changes lock the product row | System design |
| [0014](0014-multiple-instances-by-default.md) | Every service runs as several instances | Scalability |
| [0015](0015-incident-runs-are-leased-by-time.md) | Incident runs are leased by time | Agent engineering |
| [0016](0016-one-audit-trail-linked-to-traces.md) | One audit trail, linked to traces | Observability |
| [0017](0017-test-guards-deterministically-and-agents-by-outcome.md) | Test the guards deterministically and the agents by outcome | Testing |
| [0018](0018-java-and-spring-ai-for-agents.md) | Java and Spring AI for the agents | Technology |
| [0019](0019-chat-memory-in-postgres-tied-to-the-customer.md) | Chat memory in Postgres, tied to the customer | Agent engineering |
| [0020](0020-customers-see-outcomes-back-office-sees-who-acted.md) | Customers see outcomes; the back office sees who acted | Product design |
| [0021](0021-servicenow-integration-by-batched-polling.md) | Integrate with ServiceNow by batched polling of its Table API | Integration |

## Proposed

| ADR | Limitation it addresses | Expected solution |
|---|---|---|
| [0022](0022-mcp-gateway-with-policy-as-code.md) | Governance is a library in every server; no single entry point | MCP gateway with policy as code |
| [0023](0023-workload-identity-for-agents.md) | Long-lived shared tokens | Short-lived, scoped tokens or workload identity |
| [0024](0024-llm-gateway-for-data-residency.md) | Tool results go to a cloud model unfiltered | LLM gateway: in-region model, redaction, budgets |
| [0025](0025-durable-workflows-for-incident-runs.md) | Incident runs coordinated by timeouts | Durable workflows (Temporal) |
| [0026](0026-tamper-evident-audit-trail.md) | Audit trail can be edited | Append-only, hash-chained, shipped to SIEM |
| [0027](0027-separate-governance-from-the-commerce-tools.md) | Commerce MCP server is also the governance store | A separate governance service |
| [0028](0028-evals-in-ci-and-a-prompt-injection-suite.md) | Evals run by hand; no adversarial tests | Evals in CI, nightly runs, injection suite |
| [0029](0029-per-customer-chat-limits.md) | No per-customer chat limits | Rate and token limits per customer |
| [0030](0030-hot-product-stock-contention.md) | One hot product limits checkout | Stock buckets or Redis reservation |
| [0031](0031-login-and-operator-roles.md) | No login; anyone can approve or switch | OIDC login and operator roles |

## Writing a new record

Copy the shape of an existing one: Status, Date, Related; Context; Decision; Options considered; Consequences; In the
code; In an interview. Take the next number, and add it to the tables above.
