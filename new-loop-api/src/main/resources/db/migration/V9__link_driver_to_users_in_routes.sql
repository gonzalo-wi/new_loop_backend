ALTER TABLE routes DROP COLUMN driver;
ALTER TABLE routes ADD COLUMN driver_id UUID REFERENCES users(id);

CREATE INDEX idx_routes_driver_id ON routes (driver_id);
