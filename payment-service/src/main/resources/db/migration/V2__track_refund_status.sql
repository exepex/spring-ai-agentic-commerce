-- Where each refund stands at the card processor. A refund can be pending, and even a succeeded one can still fail
-- later; a failed refund returned no money, so it no longer counts towards the payment's refunded amount.
alter table refund add column status varchar(16) not null default 'SUCCEEDED';
alter table refund alter column status drop default;
