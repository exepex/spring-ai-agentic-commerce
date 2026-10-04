# ADR-0020: Customers see outcomes; the back office sees who acted

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0016

## Context

The UI serves two audiences. Customers want to know what is happening with their order. The back office (customer care,
operations) needs to know who did what. Internal names such as "order-service" mean nothing to either.

## Decision

- **Customer pages** (Shop, My orders, Your order) never name agents, people or services. The order page says in one
  sentence where the order is and what happens next. Refunds waiting for approval read "being reviewed" and failed ones
  read "hasn't gone through yet". A customer sees only their own orders.
- **Back-office pages** (All orders, Operations) name agents ("Incident agent") and people. ServiceNow keeps its name,
  because it is the care team's own tool. Steps the shop's services take on their own read **Automatic**. The internal
  service, the raw action and the trace are under each step's "Technical details". Automatic steps are shown by
  default, with filters for agents, people and automatic steps.

## Consequences

- Customers aren't shown internal details, and care staff read business events ("Refund issued", "Case opened").
- Engineers still get the service name and trace one click away.

## In the code

- `shop-ui/src/app/features/my-orders/customer-order-page.*`, `shared/audit-timeline.*`, `core/labels.ts`

## In an interview

**Q: Who sees the audit trail?** Only the back office, in business terms. Customers see outcomes and next steps, not who
did the work.
