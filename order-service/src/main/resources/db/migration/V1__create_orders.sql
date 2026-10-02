create table customer_order (
    id                   uuid primary key,
    customer_email       varchar(320)   not null,
    status               varchar(16)    not null,
    total_amount         numeric(12, 2) not null,
    currency             char(3)        not null,
    created_at           timestamptz    not null,
    cancelled_at         timestamptz,
    cancellation_reason  varchar(500)
);

create index customer_order_email on customer_order (customer_email, created_at desc);

create table order_line (
    id            uuid primary key,
    order_id      uuid           not null references customer_order (id),
    product_id    uuid           not null,
    sku           varchar(64)    not null,
    product_name  varchar(200)   not null,
    quantity      integer        not null check (quantity > 0),
    unit_price    numeric(12, 2) not null
);

create index order_line_order on order_line (order_id);
