-- The reconciler looks for stalled checkouts every few seconds; only orders still being placed are indexed.
create index customer_order_unsettled on customer_order (created_at) where status in ('PLACED', 'PAYMENT_PENDING');

-- The operations console lists the most recent orders.
create index customer_order_recent on customer_order (created_at desc);

-- One reconciler at a time across the service's instances (ShedLock).
create table shedlock (
    name       varchar(64)  primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);
