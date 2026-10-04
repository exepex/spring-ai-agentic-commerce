-- Built without blocking writes, so a rolling deployment can add it while the other instances take orders. Each
-- statement runs on its own (see the .conf file next to this one): CONCURRENTLY cannot run in a transaction. A build
-- that was interrupted leaves an unusable index behind, so each index is dropped first: running the migration again
-- builds it anew.

-- The operations console lists the most recent shipments.
drop index concurrently if exists shipment_recent;
create index concurrently shipment_recent on shipment (created_at desc);
