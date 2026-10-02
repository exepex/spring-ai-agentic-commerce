-- An escalation is worked by one person at a time: whoever assigned it to themselves.
alter table escalation add column assigned_to varchar(320);
alter table escalation add column assigned_at timestamptz;
