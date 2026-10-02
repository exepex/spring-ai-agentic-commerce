-- A refund request is versioned, so a late answer from the payment service cannot overwrite a failure the card
-- processor reported meanwhile.
alter table refund_request add column version bigint not null default 0;
