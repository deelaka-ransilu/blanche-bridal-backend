ALTER TABLE payments DROP CONSTRAINT payments_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_method_check
    CHECK (method IN ('PAYHERE', 'CASH', 'CARD', 'BANK_TRANSFER'));

ALTER TABLE orders DROP CONSTRAINT orders_payment_method_check;
ALTER TABLE orders ADD CONSTRAINT orders_payment_method_check
    CHECK (payment_method IN ('PAYHERE', 'CASH', 'CARD', 'BANK_TRANSFER'));

ALTER TABLE payments ADD COLUMN IF NOT EXISTS proof_image_url VARCHAR(500);