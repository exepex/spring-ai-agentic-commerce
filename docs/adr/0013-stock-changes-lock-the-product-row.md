# ADR-0013: Stock changes lock the product row

- **Status:** Accepted
- **Date:** 2026-10-04
- **Related:** ADR-0030

## Context

Many checkouts and write-offs can change the same product's stock at once, across several instances of the catalog.
Read-then-write without a lock oversells.

## Decision

Every reservation, release and write-off locks the product's row for the length of its short transaction. A write-off
that leaves fewer units on hand than reserved publishes a stock-out, and the catalog (not a model) computes which
orders it affects.

## Consequences

- Stock is never oversold, whichever instance handles the request.
- Checkout for one product is bound by Postgres commit rate. In the load test every order locks one of two product
  rows, and checkout still reached 48 orders/s with a 95th percentile of 430 ms on one 4-vCPU machine.
- More instances scale stateless work, not a single product's stock. A flash sale needs ADR-0030.

## In an interview

**Q: How do you prevent overselling?** A row lock per product for each stock change, in a short transaction with no
remote call inside it.

**Q: Where does that stop scaling?** On one very hot product. I'd split its stock into buckets or reserve in Redis with
a durable record behind it (ADR-0030).
