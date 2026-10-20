-- Some returnables (pallets, racks, carts, crates) always come back full: "full" is the same as "total".
-- Flagged products hide the "Llenos" field in the app and the backend stores full = total for them.
ALTER TABLE products ADD COLUMN full_equals_total BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE products SET full_equals_total = TRUE WHERE code IN ('PM', 'ZR', 'ZC', '3');
