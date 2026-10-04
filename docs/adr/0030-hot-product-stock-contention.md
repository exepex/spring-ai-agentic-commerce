# ADR-0030: Handling hot-product stock contention

- **Status:** Proposed
- **Date:** 2026-10-04
- **Related:** ADR-0013

## Context (the limitation today)

Every stock change locks the product's row (ADR-0013). That never oversells, but checkout for one very popular product
is limited by how fast Postgres can commit changes to that one row. A flash sale on one product would queue.

## Proposed decision

Only when a product needs it:

- **Stock buckets:** split a hot product's stock into N rows and reserve from a random bucket, falling back to others;
  contention drops by about N.
- **Or reserve in Redis** with an atomic decrement, and record each reservation durably through the outbox so it can be
  reconciled.
- Keep the row lock for normal products.

## In an interview

**Q: Where is your scaling limit?** One hot product's stock row. I'd shard that product's stock into buckets, or reserve
in Redis with a durable record behind it, and keep the simple lock for everything else.
