-- A confirmed proposal keeps its payment method and when it was confirmed, so a confirmation whose outcome is not
-- known yet can place the same order again: the proposal's id is the order's id, and placing it is idempotent.
alter table order_proposal add column payment_method varchar(255);
alter table order_proposal add column confirming_since timestamptz;
