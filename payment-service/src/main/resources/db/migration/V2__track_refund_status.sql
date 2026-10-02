-- Where each refund stands at the card processor. A refund can be pending, and even a succeeded one can still fail
-- later; a failed refund returned no money, so it no longer counts towards the payment's refunded amount.
alter table refund add column status varchar(16) not null default 'SUCCEEDED';
alter table refund alter column status drop default;

-- When the refund was first seen succeeded: a succeeded refund is watched for failure from then on.
alter table refund add column succeeded_at timestamptz;
update refund set succeeded_at = created_at where status = 'SUCCEEDED';
