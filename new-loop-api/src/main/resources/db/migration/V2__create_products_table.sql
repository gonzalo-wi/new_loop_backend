CREATE TABLE products (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(150) NOT NULL,
    display_order INTEGER      NOT NULL,
    description   VARCHAR(500),
    type          VARCHAR(20)  NOT NULL,
    unit          VARCHAR(50)  NOT NULL,
    pack_quantity INTEGER,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_product_type CHECK (type IN ('RETORNABLE', 'DESCARTABLE'))
);
