-- Odoo tracking for the "equipos no normalizados" intake (UNLOAD serials flagged as unregistered in jMobile).
-- Separate from odoo_status/odoo_picking_*: the same UNLOAD can produce both a repair picking (registered
-- serials) and a no-normalizados picking (unregistered serials).
ALTER TABLE dispenser_movements ADD COLUMN odoo_no_normalizado_status VARCHAR(20);
ALTER TABLE dispenser_movements ADD COLUMN odoo_no_normalizado_picking_id INTEGER;
ALTER TABLE dispenser_movements ADD COLUMN odoo_no_normalizado_picking_name VARCHAR(100);
