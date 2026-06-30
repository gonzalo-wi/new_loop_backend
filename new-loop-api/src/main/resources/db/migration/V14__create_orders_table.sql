CREATE TABLE orders (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id     UUID         NOT NULL REFERENCES routes(id),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    order_date   DATE         NOT NULL,
    observations VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE order_items (
    id                   UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id             UUID    NOT NULL REFERENCES orders(id),
    orderable_product_id UUID    NOT NULL REFERENCES orderable_products(id),
    unit_quantity        INTEGER CHECK (unit_quantity >= 0),
    bulk_quantity        INTEGER CHECK (bulk_quantity >= 0),
    UNIQUE (order_id, orderable_product_id)
);

CREATE INDEX idx_orders_route_id  ON orders (route_id);
CREATE INDEX idx_orders_status    ON orders (status);
CREATE INDEX idx_orders_date      ON orders (order_date);
