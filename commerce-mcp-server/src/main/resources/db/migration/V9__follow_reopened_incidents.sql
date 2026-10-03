-- A resolved incident can be reopened until ServiceNow closes or cancels it. Then it is final, and its case is no
-- longer read back.
alter table support_case add column incident_final boolean not null default false;
