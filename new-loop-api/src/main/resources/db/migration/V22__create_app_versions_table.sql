CREATE TABLE app_versions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    version       VARCHAR(20)  NOT NULL,
    apk_file_name VARCHAR(255) NOT NULL,
    mandatory     BOOLEAN      NOT NULL DEFAULT FALSE,
    notes         VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_versions_created_at ON app_versions (created_at DESC);
