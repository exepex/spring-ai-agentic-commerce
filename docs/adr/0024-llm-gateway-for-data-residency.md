# ADR-0024: An LLM gateway for data residency, redaction and budgets

- **Status:** Proposed
- **Date:** 2026-10-04
- **Related:** ADR-0022, ADR-0029

## Context (the limitation today)

agent-service calls Anthropic's API directly. Every prompt and every tool result (customer emails, order details) is
sent to the model as is. A regulated organisation, such as a bank, may require that personal or confidential data
stays in its network or region.

## Proposed decision

Route all model calls through an **LLM gateway** inside the network:

- **Where the model runs:** Claude through a cloud provider the organisation already trusts (Amazon Bedrock or Google
  Vertex AI) in its region over private networking, or a self-hosted model for the most sensitive data.
- **Data minimisation:** tools return only the fields the next decision needs; the gateway masks personal data
  (emails, names, addresses) and can restore it in responses where needed.
- **Routing by data class:** sensitive requests go to the in-region or self-hosted model.
- **Budgets and rate limits** per agent and per customer (ADR-0029).
- **Fallback** to another region or model when the primary is down.
- **Egress control:** outbound traffic only to approved model endpoints.

## Expected solution (sketch)

1. Put Spring AI's model client behind a configurable base URL that points at the gateway.
2. Add a redaction step with reversible tokens for identifiers the tools need back.
3. Classify tools by the data they return; route accordingly.

## In an interview

**Q: Our data can't leave the network. Does this work?** The services and MCP servers already run on-premises. The real
question is the model: use it in-region through a trusted provider or self-host it, minimise what tools return, and
enforce redaction and egress at a gateway. The number of MCP servers doesn't change that.
