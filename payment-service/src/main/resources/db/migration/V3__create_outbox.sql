-- Events wait here, written in the same transaction as the change they announce, until the relay has put them on
-- Kafka. The relay reads them oldest first and deletes each one Kafka took.
create table outbox_event (
    id            bigint generated always as identity primary key,
    topic         varchar(255) not null,
    event_key     varchar(255),
    payload       text         not null,
    trace_headers text,
    created_at    timestamptz  not null default now()
);
