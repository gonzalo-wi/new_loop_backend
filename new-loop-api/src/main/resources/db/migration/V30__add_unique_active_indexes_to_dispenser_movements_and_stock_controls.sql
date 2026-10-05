-- Backs the services' check-then-act duplicate validation: two concurrent requests can both pass
-- the exists() check, so the database is the one that guarantees a single active record.
-- Cancelled records are excluded, matching the services' "StatusNot(CANCELLED)" checks.
CREATE UNIQUE INDEX uq_dispenser_movements_type_route_date_active
    ON dispenser_movements (type, route_code, movement_date)
    WHERE status <> 'CANCELLED';

CREATE UNIQUE INDEX uq_stock_controls_type_route_date_active
    ON stock_controls (type, route_id, control_date)
    WHERE status <> 'CANCELLED';
