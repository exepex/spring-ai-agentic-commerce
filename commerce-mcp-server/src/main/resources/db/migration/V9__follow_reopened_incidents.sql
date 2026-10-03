-- A resolved incident can be reopened until ServiceNow closes or cancels it. Then it is final, and its case is no
-- longer read back.
alter table support_case add column incident_final boolean not null default false;

-- A case whose incident was reopened while the order already had a newer open case of the same problem is open again,
-- so agents leave the order's money to whoever has it, but the newer case stays the one a problem raised again goes to.
alter table support_case add column reopened_beside_open_case boolean not null default false;
drop index support_case_open;
create unique index support_case_open on support_case (order_id, type)
    where status <> 'RESOLVED' and order_id is not null and type <> 'SERVICE_DESK' and not reopened_beside_open_case;
