-- Read-only plant roles: VISUALIZADOR follows its branch's stock controls from the panel and
-- CLARKISTA reads the load forecast from the app. Both need a branch, enforced by the application.
ALTER TABLE users DROP CONSTRAINT chk_user_role;

ALTER TABLE users ADD CONSTRAINT chk_user_role CHECK (role IN (
    'ADMIN',
    'CONTROLADOR',
    'REPARTIDOR',
    'SUPERVISOR',
    'VISUALIZADOR',
    'CLARKISTA'
));
