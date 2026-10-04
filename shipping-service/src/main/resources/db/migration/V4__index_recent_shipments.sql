-- The operations console lists the most recent shipments.
create index shipment_recent on shipment (created_at desc);
