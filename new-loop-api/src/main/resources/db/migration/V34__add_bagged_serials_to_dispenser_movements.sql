-- UNLOAD serials that came back still bagged (new dispenser the driver could not deliver), so the plant can
-- skip repair for them. Internal only: never sent to Aguas or Odoo.
CREATE TABLE dispenser_movement_bagged_serials (
    movement_id UUID         NOT NULL REFERENCES dispenser_movements(id) ON DELETE CASCADE,
    serial      VARCHAR(100) NOT NULL
);

CREATE INDEX idx_dispenser_bagged_serials_movement ON dispenser_movement_bagged_serials (movement_id);
