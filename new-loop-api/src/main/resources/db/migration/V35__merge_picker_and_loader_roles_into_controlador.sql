-- PICKER and CARGADOR_DISPENSERS were the same job as CONTROLADOR. Users are moved before the
-- constraint is tightened, so no row is left with a role the application no longer knows.
-- audit_logs.user_role keeps the historical values on purpose.
UPDATE users
SET role       = 'CONTROLADOR',
    updated_at = NOW()
WHERE role IN ('PICKER', 'CARGADOR_DISPENSERS');

ALTER TABLE users DROP CONSTRAINT chk_user_role;

ALTER TABLE users ADD CONSTRAINT chk_user_role CHECK (role IN (
    'ADMIN',
    'CONTROLADOR',
    'REPARTIDOR',
    'SUPERVISOR'
));
