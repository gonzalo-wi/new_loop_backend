-- Several operators print exit remitos from one shared panel session, so whether a remito was
-- already printed has to live on the control. Existing controls start as not printed.
ALTER TABLE stock_controls ADD COLUMN remito_printed BOOLEAN NOT NULL DEFAULT FALSE;
