-- Built without blocking writes, so a rolling deployment can add it while the other instances take orders. Each
-- statement runs on its own (see the .conf file next to this one): CONCURRENTLY cannot run in a transaction.

-- The reconciler looks for confirmations that did not finish; only those still confirming are indexed.
create index concurrently if not exists order_proposal_confirming on order_proposal (confirming_since)
    where status = 'CONFIRMING';

-- The operations console lists the most recent cases, refund requests and notifications.
create index concurrently if not exists support_case_recent on support_case (created_at desc);
create index concurrently if not exists refund_request_recent on refund_request (created_at desc);
create index concurrently if not exists customer_notification_recent on customer_notification (created_at desc);
