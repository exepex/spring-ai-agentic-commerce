-- The reconciler looks for confirmations that did not finish; only those still confirming are indexed.
create index order_proposal_confirming on order_proposal (confirming_since) where status = 'CONFIRMING';

-- The operations console lists the most recent cases, refund requests and notifications.
create index support_case_recent on support_case (created_at desc);
create index refund_request_recent on refund_request (created_at desc);
create index customer_notification_recent on customer_notification (created_at desc);

-- One reconciler at a time across the server's instances (ShedLock).
create table shedlock (
    name       varchar(64)  primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);
