-- A notification may carry a key, so the same message, asked for again, is sent once: an incident delivered to the
-- agent again tells the customer nothing new.
alter table customer_notification add column idempotency_key varchar(200);

create unique index customer_notification_key on customer_notification (idempotency_key);
