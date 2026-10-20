-- The driver can now reject an ENTRY control waiting for their check. The reason is kept on the
-- control so the controller sees why while correcting it; it is cleared when the control is sent
-- back to the driver (every rejection stays in the audit log). REJECTED_BY_DRIVER is already
-- allowed by the status CHECK from V6.
ALTER TABLE stock_controls ADD COLUMN driver_rejection_reason VARCHAR(500);
