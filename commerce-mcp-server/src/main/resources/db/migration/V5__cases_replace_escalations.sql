-- Every problem that needs handling is a case, worked as a ServiceNow incident; a hand-off to people is the incident's
-- assignment to a team. The escalation queue it replaces goes away, once its unresolved work is carried over below.

create table support_case (
    id                uuid primary key,
    order_id          uuid,
    type              varchar(32)   not null,
    status            varchar(16)   not null,
    title             varchar(160)  not null,
    description       varchar(4000) not null,
    raised_by         varchar(100)  not null,
    incident_number   varchar(40),
    incident_url      varchar(500),
    assignment_group  varchar(200),
    -- Its incident goes straight to the default team, not to the agent: a person already had the work.
    for_people        boolean       not null default false,
    created_at        timestamptz   not null,
    updated_at        timestamptz   not null
);

-- An order has at most one unresolved case of each type: the same problem raised again joins it.
create unique index support_case_open on support_case (order_id, type) where status <> 'RESOLVED' and order_id is not null;
create index support_case_status on support_case (status, created_at);

-- What was added to a case after it was opened, sent to its incident as a work note.
create table case_note (
    id          uuid primary key,
    case_id     uuid          not null references support_case (id),
    text        varchar(4000) not null,
    created_at  timestamptz   not null,
    sent_at     timestamptz
);

create index case_note_unsent on case_note (created_at) where sent_at is null;

-- The events that raised a case, once per order: Kafka can deliver an event again, and a redelivered one is skipped.
create table case_event (
    source_event_id  uuid not null,
    order_id         uuid not null,
    primary key (source_event_id, order_id)
);

-- Work still waiting in the escalation queue becomes a pending hand-off case, so it reaches ServiceNow and is not lost.
-- It was already for people, so its incident goes to the default team, not to the agent. An order gets one case, from
-- its oldest unresolved escalation, as an order has at most one open case of a type.
insert into support_case (id, order_id, type, status, title, description, raised_by, for_people, created_at, updated_at)
select distinct on (coalesce(order_id::text, id::text))
       id, order_id, 'HANDOFF', 'PENDING',
       '[HANDOFF] ' || case when order_id is null then 'A request ' else 'Order ' || left(order_id::text, 8) || ' ' end
           || 'needs a person',
       summary || case when assigned_to is null then ''
                       else ' (It was assigned to ' || assigned_to || ' in the escalation queue.)' end,
       raised_by, true, created_at, created_at
from escalation
where status <> 'RESOLVED'
order by coalesce(order_id::text, id::text), created_at;

drop table escalation;
