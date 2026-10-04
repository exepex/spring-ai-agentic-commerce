# ADR-0009: Fail safe, toward a human

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0007, ADR-0015

## Context

Agents fail in ways code usually doesn't: the model is unavailable, a run hits its tool-call budget, it ends without
finishing, or an operator switches the agent off mid-run. Work must never be left half done with nobody owning it.

## Decision

Every incident ends with an owner: resolved by the agent, or assigned to a team.

- **The agent** assigns it to a team when its policy says a person must decide.
- **Code** assigns it to a team when the run fails, or ends without resolving or assigning.
- **The poller** hands it over when a claimed incident isn't finished in time.
- **The kill switch** refuses an agent's tools on the next call, but still allows its hand-off tools
  (`escalate_to_human`, `assign_to_team`), so its work passes to people.

The kill switch is stored by the commerce MCP server, so it survives restarts and works even while agent-service is
down. Flipping it is audited with the person who did it.

## Consequences

- When the model is unavailable, incidents go straight to teams and the shop keeps selling.
- People may get work the agent would normally have finished; that is the intended trade-off.

## In the code

- `IncidentAgent`, `IncidentListener` (agent-service), `IncidentPoller` (ServiceNow MCP server)
- `AgentSwitches` (commerce MCP server), `AgentSwitchboard` (agent-service), `AgentKillSwitch`,
  `IncidentAgentKillSwitch`

## In an interview

**Q: What happens when Claude is down?** Checkout is unaffected. Chat says the assistant can't be reached. Incident runs
fail and code assigns each incident to a team.

**Q: Is the kill switch real or cosmetic?** Real: the servers check it on every tool call, so even a run already in
progress stops at its next call. Only the hand-off tools still work.
