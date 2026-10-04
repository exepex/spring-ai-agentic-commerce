# ADR-0031: Login and operator roles

- **Status:** Proposed
- **Date:** 2026-10-04
- **Related:** ADR-0023

## Context (the limitation today)

The demo UI has no login. You pick which customer you are, and which operator acts in the operations console. Anyone
who can reach the UI could approve refunds or flip a kill switch.

## Proposed decision

- Customers and staff sign in through the organisation's identity provider (OIDC).
- The customer's identity comes from their session, not a picker, and is what agent-service injects (ADR-0006).
- **Roles** for staff: viewing the back office, approving refunds (with a four-eyes rule above a second limit), and
  switching agents on and off.
- The governance API and back-office pages sit behind the identity provider; decisions are recorded under the signed-in
  person.

## In an interview

**Q: Who can approve a refund or switch an agent off?** In the demo, anyone, by design. In production, signed-in staff
with a specific role, with four-eyes approval for large refunds, and every decision recorded under their identity.
