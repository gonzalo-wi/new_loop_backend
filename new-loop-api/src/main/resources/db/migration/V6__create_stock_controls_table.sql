CREATE TABLE stock_controls (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type         VARCHAR(10)  NOT NULL,
    status       VARCHAR(30)  NOT NULL DEFAULT 'CONTROLLED',
    branch_id    UUID         NOT NULL REFERENCES branches(id),
    route_id     UUID         NOT NULL REFERENCES routes(id),
    control_date DATE         NOT NULL,
    observations VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_control_type CHECK (type IN ('EXIT', 'ENTRY')),
    CONSTRAINT chk_control_status CHECK (status IN (
        'CONTROLLED', 'PENDING_DRIVER_APPROVAL', 'ACCEPTED_BY_DRIVER',
        'REJECTED_BY_DRIVER', 'WITH_DIFFERENCES', 'SENT_TO_AGUAS',
        'AGUAS_ERROR', 'CANCELLED'
    ))
);

CREATE TABLE stock_control_items (
    id                  UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_control_id    UUID    NOT NULL REFERENCES stock_controls(id),
    product_id          UUID    NOT NULL REFERENCES products(id),
    total_quantity      INTEGER NOT NULL DEFAULT 0,
    full_quantity       INTEGER NOT NULL DEFAULT 0,
    exchange_quantity   INTEGER NOT NULL DEFAULT 0,
    difference_quantity INTEGER,
    observations        VARCHAR(500),

    CONSTRAINT chk_total_qty    CHECK (total_quantity    >= 0),
    CONSTRAINT chk_full_qty     CHECK (full_quantity     >= 0),
    CONSTRAINT chk_exchange_qty CHECK (exchange_quantity >= 0),
    UNIQUE (stock_control_id, product_id)
);

CREATE INDEX idx_stock_controls_branch ON stock_controls (branch_id);
CREATE INDEX idx_stock_controls_route  ON stock_controls (route_id);
CREATE INDEX idx_stock_controls_date   ON stock_controls (control_date);
CREATE INDEX idx_stock_items_control   ON stock_control_items (stock_control_id);
