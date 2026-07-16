ALTER TABLE dispenser_movements ADD COLUMN odoo_status       VARCHAR(20);
ALTER TABLE dispenser_movements ADD COLUMN odoo_picking_id   INTEGER;
ALTER TABLE dispenser_movements ADD COLUMN odoo_picking_name VARCHAR(100);
