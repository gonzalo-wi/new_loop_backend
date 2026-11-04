-- Holiday calendar (spec 005): on these dates the remito is printed without the company data.
-- It applies to every branch, so there is no branch column.
CREATE TABLE holidays (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date DATE         NOT NULL,
    description  VARCHAR(120),
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_holidays_date UNIQUE (holiday_date)
);
