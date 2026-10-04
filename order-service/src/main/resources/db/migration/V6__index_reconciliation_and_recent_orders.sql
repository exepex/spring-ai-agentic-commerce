-- Built without blocking writes, so a rolling deployment can add it while the other instances take orders. Each
-- statement runs on its own (see the .conf file next to this one): CONCURRENTLY cannot run in a transaction.

-- The reconciler looks for stalled checkouts every few seconds; only orders still being placed are indexed.
create index concurrently if not exists customer_order_unsettled on customer_order (created_at)
    where status in ('PLACED', 'PAYMENT_PENDING');

-- The operations console lists the most recent orders.
create index concurrently if not exists customer_order_recent on customer_order (created_at desc);
