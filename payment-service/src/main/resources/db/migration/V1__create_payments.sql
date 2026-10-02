create table payment (
    id                  uuid primary key,
    order_id            uuid           not null unique,
    customer_email      varchar(320)   not null,
    amount              numeric(12, 2) not null check (amount > 0),
    refunded_amount     numeric(12, 2) not null check (refunded_amount >= 0),
    currency            char(3)        not null,
    status              varchar(16)    not null,
    provider            varchar(16)    not null,
    provider_reference  varchar(255),
    failure_message     varchar(500),
    created_at          timestamptz    not null
);

create table refund (
    id                  uuid primary key,
    payment_id          uuid           not null references payment (id),
    amount              numeric(12, 2) not null check (amount > 0),
    reason              varchar(500)   not null,
    idempotency_key     varchar(200)   not null unique,
    provider_reference  varchar(255)   not null,
    created_at          timestamptz    not null
);

create index refund_payment on refund (payment_id);
