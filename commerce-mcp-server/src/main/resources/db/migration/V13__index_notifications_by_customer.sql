-- Built without blocking writes, so a rolling deployment can add it while the other instances take orders. Each
-- statement runs on its own (see the .conf file next to this one): CONCURRENTLY cannot run in a transaction. A build
-- that was interrupted leaves an unusable index behind, so the index is dropped first: running the migration again
-- builds it anew.

-- The shop shows each customer their own most recent messages.
drop index concurrently if exists customer_notification_customer;
create index concurrently customer_notification_customer on customer_notification (customer_email, created_at desc);
