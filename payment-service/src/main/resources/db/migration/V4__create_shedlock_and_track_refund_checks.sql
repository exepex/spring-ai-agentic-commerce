-- One reconciler at a time across the service's instances (ShedLock).
create table shedlock (
    name       varchar(64)  primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);

-- When the refund check last asked the processor about a refund: it asks about the least recently checked first, so
-- refunds that stay pending cannot keep the others from being checked.
alter table refund add column checked_at timestamptz;
