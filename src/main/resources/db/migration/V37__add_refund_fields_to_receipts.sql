ALTER TABLE receipts ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE receipts ALTER COLUMN payment_id DROP NOT NULL;

ALTER TABLE receipts ADD COLUMN rental_id UUID UNIQUE REFERENCES rentals(id);
ALTER TABLE receipts ADD COLUMN receipt_type VARCHAR(20) NOT NULL DEFAULT 'PAYMENT'
    CHECK (receipt_type IN ('PAYMENT', 'REFUND'));
ALTER TABLE receipts ALTER COLUMN receipt_type DROP DEFAULT;

-- Backfill existing rows explicitly (they're all payment receipts).
UPDATE receipts SET receipt_type = 'PAYMENT' WHERE receipt_type IS NULL;