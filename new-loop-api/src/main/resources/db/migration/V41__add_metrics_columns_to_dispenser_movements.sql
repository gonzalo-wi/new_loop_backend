-- Operational metrics (spec 001) for dispenser movements, which are already kept forever.
-- load_duration_seconds: measured by the app; NULL for older apps and implausible values.
-- branch_id: the route's branch when the movement was registered (movements only store the route code).
-- replaces_movement_id: an edit cancels the old movement and creates a new one; this links them so the
-- edit is not counted as a cancellation.
ALTER TABLE dispenser_movements
    ADD COLUMN load_duration_seconds INTEGER,
    ADD COLUMN branch_id             UUID,
    ADD COLUMN replaces_movement_id  UUID REFERENCES dispenser_movements (id);

-- Existing movements take their route's current branch: the closest value available for history.
-- Operators type only the number ("179") while routes are named "Rto 179", so both sides are compared
-- by their digits (same rule as RouteNumber). A number shared by several routes is left without branch.
WITH route_numbers AS (
    SELECT r.branch_id,
           CASE
               WHEN length(regexp_replace(r.code, '[^0-9]', '', 'g')) BETWEEN 1 AND 18
               THEN CAST(regexp_replace(r.code, '[^0-9]', '', 'g') AS BIGINT)
           END AS route_number
    FROM routes r
),
unique_route_numbers AS (
    SELECT route_number, min(branch_id::text)::uuid AS branch_id
    FROM route_numbers
    WHERE route_number IS NOT NULL
    GROUP BY route_number
    HAVING count(*) = 1
)
UPDATE dispenser_movements m
SET branch_id = u.branch_id
FROM unique_route_numbers u
WHERE u.route_number = CASE
                           WHEN length(regexp_replace(m.route_code, '[^0-9]', '', 'g')) BETWEEN 1 AND 18
                           THEN CAST(regexp_replace(m.route_code, '[^0-9]', '', 'g') AS BIGINT)
                       END;

CREATE INDEX idx_dispenser_movements_branch ON dispenser_movements (branch_id);
CREATE INDEX idx_dispenser_movements_replaces ON dispenser_movements (replaces_movement_id);
