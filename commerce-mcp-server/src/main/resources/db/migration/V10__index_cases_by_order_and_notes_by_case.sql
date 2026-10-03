-- An order's cases are read on every refund, to check whether people have the order, and on every order page. The
-- partial unique index covers only open cases of the shop's own problems, so these lookups need their own.
create index support_case_order on support_case (order_id, created_at);

-- A case's unsent notes are read when its incident is resolved, to carry them over to a new case.
create index case_note_case_unsent on case_note (case_id, created_at) where sent_at is null;
