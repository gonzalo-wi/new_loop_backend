CREATE TABLE audit_logs (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,
    user_role   VARCHAR(30),
    action      VARCHAR(100) NOT NULL,
    entity_name VARCHAR(100) NOT NULL,
    entity_id   UUID,
    old_value   TEXT,
    new_value   TEXT,
    reason      VARCHAR(500),
    source      VARCHAR(20)  NOT NULL DEFAULT 'ADMIN_WEB',
    ip_address  VARCHAR(45),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity     ON audit_logs (entity_name, entity_id);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_user_id    ON audit_logs (user_id);
