create table if not exists outbox_event (
    id            bigint generated always as identity primary key,
    topic         varchar(255) not null,
    event_key     varchar(255),
    payload       text         not null,
    trace_headers text,
    created_at    timestamptz  not null default now()
);
