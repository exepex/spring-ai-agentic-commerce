-- Built without blocking writes, so a rolling deployment can add it while the other instances take orders. Each
-- statement runs on its own (see the .conf file next to this one): CONCURRENTLY cannot run in a transaction.

-- The operations console lists the most recent shipments.
create index concurrently if not exists shipment_recent on shipment (created_at desc);
