-- Built without blocking writes, so a rolling deployment can add it while the other instances take orders. Each
-- statement runs on its own (see the .conf file next to this one): CONCURRENTLY cannot run in a transaction.

-- The refund check reads pending refunds and those that succeeded recently, every minute.
create index concurrently if not exists refund_unsettled on refund (status, succeeded_at)
    where status in ('PENDING', 'SUCCEEDED');
