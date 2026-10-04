# ADR-0012: No distributed transactions; finish unfinished work later

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0011

## Context

Checkout touches stock, orders and payments. A payment call can time out with an unknown outcome. Holding a database
transaction open across that call would block stock and connections; guessing the outcome would oversell or double
charge.

## Decision

- **No database transaction is held open across a remote call.** Checkout saves the order before charging the card,
  and releases the stock if any step fails.
- **Unknown outcomes wait.** If a payment's outcome is unknown, the order waits as `PAYMENT_PENDING` with its stock
  kept, and the payment is asked for again until it succeeds or is declined.
- **Reconcilers finish the rest.** Stock to give back is recorded with the order change that needs it and released once
  the catalog answers. Refunds are checked with the processor again; one that failed afterwards stops counting as
  refunded, and a `REFUND_FAILED` case opens.

## Consequences

- No double charge and no lost stock when a dependency is slow or down.
- Some states are temporary (`PAYMENT_PENDING`), and the UI must show them honestly.
- Each service runs its own reconciler on a schedule (`commerce.reconciliation`, `commerce.payments.refund-check`).

## In an interview

**Q: Saga or two-phase commit?** A saga with compensations and reconcilers. An unknown outcome is never guessed; it is
asked again until it is known.
