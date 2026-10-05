-- LOAD serials that Aguas received but Odoo left out of the dispatch (not in expedición): before this they
-- only survived inside the integration log's error text.
CREATE TABLE dispenser_movement_odoo_skipped_serials (
    movement_id UUID         NOT NULL REFERENCES dispenser_movements(id) ON DELETE CASCADE,
    serial      VARCHAR(100) NOT NULL
);

CREATE INDEX idx_dispenser_odoo_skipped_serials_movement ON dispenser_movement_odoo_skipped_serials (movement_id);

-- Serial history lookup matches case-insensitively.
CREATE INDEX idx_dispenser_serials_upper_serial ON dispenser_movement_serials (UPPER(serial));
