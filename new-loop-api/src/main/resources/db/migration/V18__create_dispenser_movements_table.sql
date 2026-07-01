CREATE TABLE dispenser_movements (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type                   VARCHAR(10)  NOT NULL CHECK (type IN ('LOAD', 'UNLOAD')),
    route_code             VARCHAR(50)  NOT NULL,
    technician             VARCHAR(255) NOT NULL,
    location_id            INTEGER      NOT NULL,
    state_id               INTEGER      NOT NULL,
    movement_date          DATE         NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED'
                                        CHECK (status IN ('REGISTERED', 'SENT_TO_AGUAS', 'AGUAS_ERROR')),
    registered_by          UUID,
    registered_by_username VARCHAR(255),
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE dispenser_movement_serials (
    movement_id UUID         NOT NULL REFERENCES dispenser_movements(id) ON DELETE CASCADE,
    serial      VARCHAR(100) NOT NULL
);

CREATE INDEX idx_dispenser_movements_route  ON dispenser_movements (route_code);
CREATE INDEX idx_dispenser_movements_status ON dispenser_movements (status);
CREATE INDEX idx_dispenser_movements_date   ON dispenser_movements (movement_date);
CREATE INDEX idx_dispenser_serials_movement ON dispenser_movement_serials (movement_id);
