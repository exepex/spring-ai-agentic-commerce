-- A refund request is versioned, so two answers about the same refund cannot silently overwrite each other, and it
-- says whether the card processor failed it: that failure stands, whatever answer arrives late.
alter table refund_request add column version bigint not null default 0;
alter table refund_request add column failed_at_processor boolean not null default false;
