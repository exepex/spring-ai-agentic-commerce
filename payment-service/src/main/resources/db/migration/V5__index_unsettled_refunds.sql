-- Built without blocking writes, so a rolling deployment can add it while the other instances take orders. Each
-- statement runs on its own (see the .conf file next to this one): CONCURRENTLY cannot run in a transaction. A build
-- that was interrupted leaves an unusable index behind, so each index is dropped first: running the migration again
-- builds it anew.

-- The refund check reads pending refunds and those that succeeded recently, every minute.
drop index concurrently if exists refund_unsettled;
create index concurrently refund_unsettled on refund (status, succeeded_at)
    where status in ('PENDING', 'SUCCEEDED');
