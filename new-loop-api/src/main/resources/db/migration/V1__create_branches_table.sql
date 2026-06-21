CREATE TABLE branches (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150)  NOT NULL,
    code        VARCHAR(50)   NOT NULL UNIQUE,
    address     VARCHAR(255),
    locality    VARCHAR(100),
    province    VARCHAR(100),
    cuit        VARCHAR(20),
    vat_condition VARCHAR(50),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);
