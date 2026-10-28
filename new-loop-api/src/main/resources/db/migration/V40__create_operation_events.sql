-- Durable facts for operational metrics (spec 001). Unlike audit_logs they are never purged, and
-- they keep snapshots of usernames and route codes so later renames don't rewrite history.
-- No foreign keys to users, branches or routes for the same reason.
CREATE TABLE operation_events (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type           VARCHAR(40)  NOT NULL,
    occurred_at          TIMESTAMP    NOT NULL,
    event_date           DATE         NOT NULL,
    subject_type         VARCHAR(30)  NOT NULL,
    subject_id           UUID         NOT NULL,
    control_type         VARCHAR(10),
    branch_id            UUID,
    route_id             UUID,
    route_code           VARCHAR(100),
    actor_user_id        UUID,
    actor_username       VARCHAR(100),
    responsible_user_id  UUID,
    responsible_username VARCHAR(100),
    items_count          INTEGER,
    units_total          INTEGER,
    rejection_round      INTEGER,
    response_seconds     BIGINT,
    resubmission         BOOLEAN      NOT NULL DEFAULT FALSE,
    integration          VARCHAR(20),
    reason               VARCHAR(500),
    imported             BOOLEAN      NOT NULL DEFAULT FALSE,
    source_audit_id      UUID
);

CREATE INDEX idx_operation_events_date_branch      ON operation_events (event_date, branch_id);
CREATE INDEX idx_operation_events_responsible_date ON operation_events (responsible_user_id, event_date);
CREATE INDEX idx_operation_events_subject          ON operation_events (subject_id);
-- The history import is re-runnable: one event per audit entry at most.
CREATE UNIQUE INDEX uq_operation_events_source_audit ON operation_events (source_audit_id);

-- Raw per-product quantities: how an item's difference is computed is decided at query time.
CREATE TABLE operation_event_items (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id          UUID         NOT NULL REFERENCES operation_events (id),
    product_id        UUID         NOT NULL,
    product_name      VARCHAR(255) NOT NULL,
    total_quantity    INTEGER      NOT NULL,
    full_quantity     INTEGER      NOT NULL,
    exchange_quantity INTEGER      NOT NULL
);

CREATE INDEX idx_operation_event_items_event ON operation_event_items (event_id);

-- Append-only: a bug or a manual query must not be able to rewrite the history.
CREATE FUNCTION reject_operation_event_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'operation events are append-only (% on %)', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_operation_events_append_only
    BEFORE UPDATE OR DELETE ON operation_events
    FOR EACH ROW EXECUTE FUNCTION reject_operation_event_change();

CREATE TRIGGER trg_operation_events_no_truncate
    BEFORE TRUNCATE ON operation_events
    FOR EACH STATEMENT EXECUTE FUNCTION reject_operation_event_change();

CREATE TRIGGER trg_operation_event_items_append_only
    BEFORE UPDATE OR DELETE ON operation_event_items
    FOR EACH ROW EXECUTE FUNCTION reject_operation_event_change();

CREATE TRIGGER trg_operation_event_items_no_truncate
    BEFORE TRUNCATE ON operation_event_items
    FOR EACH STATEMENT EXECUTE FUNCTION reject_operation_event_change();
