create table product (
    id            uuid primary key,
    sku           varchar(64)    not null unique,
    name          varchar(200)   not null,
    description   varchar(2000)  not null,
    price_amount  numeric(12, 2) not null check (price_amount >= 0),
    currency      char(3)        not null,
    on_hand       integer        not null check (on_hand >= 0),
    reserved      integer        not null check (reserved >= 0)
);

create table stock_reservation (
    id          uuid primary key,
    order_id    uuid        not null,
    product_id  uuid        not null references product (id),
    quantity    integer     not null check (quantity > 0),
    status      varchar(16) not null,
    created_at  timestamptz not null,
    unique (order_id, product_id)
);

create index stock_reservation_product_status on stock_reservation (product_id, status, created_at);
