ALTER TABLE stock_controls ADD COLUMN controller_id UUID;

CREATE INDEX idx_stock_controls_controller ON stock_controls (controller_id);
