create table shipment (
    id                  uuid primary key,
    order_id            uuid         not null unique,
    customer_email      varchar(320) not null,
    tracking_number     varchar(32)  not null unique,
    status              varchar(16)  not null,
    estimated_delivery  date         not null,
    created_at          timestamptz  not null,
    cancelled_at        timestamptz
);
