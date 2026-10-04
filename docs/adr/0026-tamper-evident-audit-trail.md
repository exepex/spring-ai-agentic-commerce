# ADR-0026: A tamper-evident audit trail

- **Status:** Proposed
- **Date:** 2026-10-04
- **Related:** ADR-0016

## Context (the limitation today)

The audit trail is a Postgres table. An administrator with database access could change or delete entries, and nothing
would show it.

## Proposed decision

- Write audit entries **append-only**: no update or delete permission for the application's database user.
- **Hash-chain** the entries: each stores the hash of the previous one, so any change breaks the chain. Anchor the
  latest hash periodically in an external store.
- **Ship** entries to the organisation's SIEM or a WORM store (object storage with retention lock) for retention and
  alerting.

## In an interview

**Q: Can you trust your audit trail?** Today it's a regular table. For production I'd make it append-only,
hash-chained, and shipped to a write-once store, so tampering is detectable.
