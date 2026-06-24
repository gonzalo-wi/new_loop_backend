CREATE TABLE routes (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(50)  NOT NULL UNIQUE,
    branch_id    UUID         NOT NULL REFERENCES branches(id),
    driver       VARCHAR(150),
    truck_plate  VARCHAR(20),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    observations VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_routes_branch_id ON routes (branch_id);
