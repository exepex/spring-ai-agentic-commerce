-- A system event read from Kafka keeps the id its service gave it. Kafka can deliver an event again, so each event
-- is recorded once per order (a stock-out names several orders).
alter table audit_event add column source_event_id uuid;
create unique index audit_event_source on audit_event (source_event_id, order_id) where source_event_id is not null;

-- The kill switch of each agent. An agent without a row is on.
create table agent_switch (
    agent_id    varchar(100) primary key,
    enabled     boolean      not null,
    changed_by  varchar(320) not null,
    changed_at  timestamptz  not null
);
