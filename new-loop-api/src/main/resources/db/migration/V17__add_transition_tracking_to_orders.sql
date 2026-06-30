ALTER TABLE orders ADD COLUMN started_by         UUID;
ALTER TABLE orders ADD COLUMN started_by_name    VARCHAR(255);
ALTER TABLE orders ADD COLUMN started_at         TIMESTAMP;
ALTER TABLE orders ADD COLUMN completed_by       UUID;
ALTER TABLE orders ADD COLUMN completed_by_name  VARCHAR(255);
ALTER TABLE orders ADD COLUMN completed_at       TIMESTAMP;
