ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED'));

ALTER TABLE refunds
    ADD CONSTRAINT uq_refunds_order_id UNIQUE (order_id);