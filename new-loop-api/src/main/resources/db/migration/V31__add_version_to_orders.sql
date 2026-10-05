-- Optimistic locking: two pickers starting/completing the same order at once must not both succeed.
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
