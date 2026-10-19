-- A route can now get several LOADs on the same day (the first load plus recargas), so the
-- one-active-movement guarantee from V30 only holds for UNLOAD. Cancelled records stay excluded,
-- matching the service's "StatusNot(CANCELLED)" check.
DROP INDEX IF EXISTS uq_dispenser_movements_type_route_date_active;

CREATE UNIQUE INDEX uq_dispenser_movements_unload_route_date_active
    ON dispenser_movements (route_code, movement_date)
    WHERE type = 'UNLOAD' AND status <> 'CANCELLED';
