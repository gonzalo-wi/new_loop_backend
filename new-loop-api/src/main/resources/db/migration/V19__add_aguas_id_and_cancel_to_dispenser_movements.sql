ALTER TABLE dispenser_movements ADD COLUMN aguas_movement_id VARCHAR(50);

ALTER TABLE dispenser_movements DROP CONSTRAINT dispenser_movements_status_check;
ALTER TABLE dispenser_movements ADD CONSTRAINT dispenser_movements_status_check
    CHECK (status IN ('REGISTERED', 'SENT_TO_AGUAS', 'AGUAS_ERROR', 'CANCELLED'));
