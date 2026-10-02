-- Everything that happened to an order, who or what did it, and why.
create table audit_event (
    id           uuid primary key,
    occurred_at  timestamptz  not null,
    order_id     uuid,
    actor_type   varchar(16)  not null,
    actor        varchar(100) not null,
    action       varchar(100) not null,
    outcome      varchar(24)  not null,
    summary      text         not null,
    details      text,
    trace_id     varchar(64)
);

create index audit_event_order on audit_event (order_id, occurred_at);
create index audit_event_time on audit_event (occurred_at desc);

-- Every refund an agent asked for. The idempotency key makes a retried request return the first one.
create table refund_request (
    id                  uuid primary key,
    order_id            uuid           not null,
    amount              numeric(12, 2) not null check (amount > 0),
    currency            char(3)        not null,
    reason              varchar(1000)  not null,
    idempotency_key     varchar(200)   not null unique,
    requested_by        varchar(100)   not null,
    status              varchar(24)    not null,
    provider_reference  varchar(255),
    failure             varchar(1000),
    decided_by          varchar(320),
    decided_at          timestamptz,
    decision_note       varchar(1000),
    created_at          timestamptz    not null,
    updated_at          timestamptz    not null
);

create index refund_request_status on refund_request (status, created_at);
create index refund_request_order on refund_request (order_id);

-- Orders an agent proposed; nothing is charged until the customer confirms.
create table order_proposal (
    id              uuid primary key,
    customer_email  varchar(320)   not null,
    lines           text           not null,
    total           numeric(12, 2) not null,
    currency        char(3)        not null,
    status          varchar(16)    not null,
    order_id        uuid,
    failure         varchar(1000),
    created_at      timestamptz    not null
);

-- Messages sent to customers. The demo shows them in the UI instead of emailing them.
create table customer_notification (
    id              uuid primary key,
    order_id        uuid,
    customer_email  varchar(320)  not null,
    message         varchar(2000) not null,
    sent_by         varchar(100)  not null,
    created_at      timestamptz   not null
);

create index customer_notification_order on customer_notification (order_id);

-- Work an agent handed to a human because it could not or should not finish it.
create table escalation (
    id               uuid primary key,
    order_id         uuid,
    raised_by        varchar(100)  not null,
    summary          varchar(2000) not null,
    status           varchar(16)   not null,
    resolved_by      varchar(320),
    resolution_note  varchar(1000),
    created_at       timestamptz   not null,
    resolved_at      timestamptz
);

create index escalation_status on escalation (status, created_at);
