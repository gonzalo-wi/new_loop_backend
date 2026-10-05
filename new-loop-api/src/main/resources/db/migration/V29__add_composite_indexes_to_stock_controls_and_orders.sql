-- Dashboard counts, findControlsForDate and findRouteIdsForDate filter by date + type + status.
CREATE INDEX idx_stock_controls_date_type_status ON stock_controls (control_date, type, status);
-- Duplicate check on create: same route, type and date.
CREATE INDEX idx_stock_controls_route_type_date ON stock_controls (route_id, type, control_date);
-- Orders list default sort.
CREATE INDEX idx_orders_created_at ON orders (created_at DESC);
