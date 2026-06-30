CREATE TABLE orderable_products (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(255) NOT NULL,
    description    VARCHAR(500),
    allows_unit    BOOLEAN      NOT NULL DEFAULT TRUE,
    allows_bulk    BOOLEAN      NOT NULL DEFAULT FALSE,
    units_per_bulk INTEGER,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orderable_products_code   ON orderable_products (code);
CREATE INDEX idx_orderable_products_active ON orderable_products (active);
