-- The refund check reads pending refunds and those that succeeded recently, every minute.
create index refund_unsettled on refund (status, succeeded_at) where status in ('PENDING', 'SUCCEEDED');

-- One refund check at a time across the service's instances (ShedLock).
create table shedlock (
    name       varchar(64)  primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);
