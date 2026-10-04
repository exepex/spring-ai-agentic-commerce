-- Built without blocking writes, so a rolling deployment can add it while the other instances take orders. Each
-- statement runs on its own (see the .conf file next to this one): CONCURRENTLY cannot run in a transaction. A build
-- that was interrupted leaves an unusable index behind, so each index is dropped first: running the migration again
-- builds it anew.

-- The reconciler looks for confirmations that did not finish; only those still confirming are indexed.
drop index concurrently if exists order_proposal_confirming;
create index concurrently order_proposal_confirming on order_proposal (confirming_since)
    where status = 'CONFIRMING';

-- The operations console lists the most recent cases, refund requests and notifications.
drop index concurrently if exists support_case_recent;
create index concurrently support_case_recent on support_case (created_at desc);
drop index concurrently if exists refund_request_recent;
create index concurrently refund_request_recent on refund_request (created_at desc);
drop index concurrently if exists customer_notification_recent;
create index concurrently customer_notification_recent on customer_notification (created_at desc);
