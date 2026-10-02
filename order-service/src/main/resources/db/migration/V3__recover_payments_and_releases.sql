-- The payment method is kept so a payment whose outcome is unknown can be asked for again with the same
-- idempotency key. The version lets only one of two concurrent changes to an order win.
alter table customer_order add column payment_method varchar(255);
alter table customer_order add column version bigint not null default 0;

-- Stock that must be given back to the catalog. A row is written in the same transaction as the order change that
-- needs it and deleted once the catalog confirms, so a release is retried until it happens.
create table stock_release (
    order_id      uuid primary key,
    requested_at  timestamptz not null
);
