-- Serials scanned in an UNLOAD but flagged as "dispenser no registrado" in jMobile:
-- kept for traceability, excluded from the Aguas and Odoo payloads.
CREATE TABLE dispenser_movement_excluded_serials (
    movement_id UUID         NOT NULL REFERENCES dispenser_movements(id) ON DELETE CASCADE,
    serial      VARCHAR(100) NOT NULL
);

CREATE INDEX idx_dispenser_excluded_serials_movement ON dispenser_movement_excluded_serials (movement_id);

-- New status for movements where every scanned serial was excluded (nothing was sent to Aguas).
ALTER TABLE dispenser_movements ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE dispenser_movements DROP CONSTRAINT dispenser_movements_status_check;
ALTER TABLE dispenser_movements ADD CONSTRAINT dispenser_movements_status_check
    CHECK (status IN ('REGISTERED', 'SENT_TO_AGUAS', 'AGUAS_ERROR', 'SKIPPED_UNREGISTERED', 'CANCELLED'));
