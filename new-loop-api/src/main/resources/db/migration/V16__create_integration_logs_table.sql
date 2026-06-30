CREATE TABLE integration_logs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    integration_name VARCHAR(20)  NOT NULL,
    operation_type   VARCHAR(20)  NOT NULL,
    entity_name      VARCHAR(100) NOT NULL,
    entity_id        UUID,
    status           VARCHAR(20)  NOT NULL,
    request_payload  TEXT,
    response_payload TEXT,
    error_message    TEXT,
    retry_count      INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    sent_at          TIMESTAMP
);

CREATE INDEX idx_integration_logs_status    ON integration_logs (status);
CREATE INDEX idx_integration_logs_entity_id ON integration_logs (entity_id);
CREATE INDEX idx_integration_logs_name      ON integration_logs (integration_name);
