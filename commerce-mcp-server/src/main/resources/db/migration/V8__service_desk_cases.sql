-- An incident the service desk raised about an order is recorded as a case too, so agents leave the order's money to
-- whoever works it. An order can have several such incidents at once, so the one-open-case-per-type rule leaves them
-- out. Each such incident is recorded once, known by its link: its number alone repeats across instances.
drop index support_case_open;
create unique index support_case_open on support_case (order_id, type)
    where status <> 'RESOLVED' and order_id is not null and type <> 'SERVICE_DESK';
create unique index support_case_service_desk on support_case (incident_url) where type = 'SERVICE_DESK';
