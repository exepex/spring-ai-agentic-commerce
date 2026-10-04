-- Built without blocking writes, so a rolling deployment can add it while the other instances take orders. Each
-- statement runs on its own (see the .conf file next to this one): CONCURRENTLY cannot run in a transaction. A build
-- that was interrupted leaves an unusable index behind, so each index is dropped first: running the migration again
-- builds it anew.

-- The reconciler looks for stalled checkouts every few seconds; only orders still being placed are indexed.
drop index concurrently if exists customer_order_unsettled;
create index concurrently customer_order_unsettled on customer_order (created_at)
    where status in ('PLACED', 'PAYMENT_PENDING');

-- The operations console lists the most recent orders.
drop index concurrently if exists customer_order_recent;
create index concurrently customer_order_recent on customer_order (created_at desc);

-- Stock releases the catalog has not confirmed yet are retried oldest first, a batch at a time.
drop index concurrently if exists stock_release_requested;
create index concurrently stock_release_requested on stock_release (requested_at);
