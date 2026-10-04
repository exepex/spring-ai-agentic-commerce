# ADR-0023: Short-lived workload identity for agents and services

- **Status:** Proposed
- **Date:** 2026-10-04
- **Related:** ADR-0003, ADR-0022

## Context (the limitation today)

Each agent has a long-lived bearer token from environment variables, shared by agent-service and the MCP servers. The
services' internal token works the same way. A leaked token works until someone rotates it by hand.

## Proposed decision

- Agents and services get **short-lived tokens** from the organisation's identity provider: OAuth 2.0 client
  credentials, or workload identity (SPIFFE/SPIRE, cloud workload identity) with mTLS between services.
- Each token is **scoped** to one agent and the servers it may call (audience).
- The MCP servers (or the gateway) validate signature, expiry, audience and scope instead of comparing a shared secret.
- MCP's authorization specification (OAuth-based) is used where clients support it.

## Expected solution (sketch)

1. Register one client per agent in the identity provider; keep the agent id as a claim.
2. agent-service fetches and refreshes tokens; nothing long-lived in configuration.
3. Replace `AgentAuthenticationFilter`'s token lookup with JWT validation; keep resolving the agent from the token.
4. Rotate signing keys automatically.

## In an interview

**Q: How do agents authenticate?** Today, with a token per agent, checked on every call; the agent is resolved from the
token, never from the model. In production I'd use short-lived, scoped tokens from the identity provider, or workload
identity with mTLS, so there is no shared secret to leak.
