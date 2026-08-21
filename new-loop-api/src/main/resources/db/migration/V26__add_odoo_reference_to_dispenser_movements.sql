-- External reference sent to Odoo on the salida/create call (LOAD -> dispatch to reparto).
-- Kept for traceability/support and so a retry can recompute and audit the same reference Odoo saw.
ALTER TABLE dispenser_movements ADD COLUMN odoo_reference VARCHAR(100);
