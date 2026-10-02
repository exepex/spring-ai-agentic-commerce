alter table shipment add column shipped_at timestamptz;
alter table shipment add column delivered_at timestamptz;
alter table shipment add column delivery_problem varchar(500);

create index shipment_status on shipment (status, created_at desc);
