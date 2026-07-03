CREATE TABLE payments (
                          id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                          order_id            UUID          UNIQUE REFERENCES orders(id) ON DELETE SET NULL,
                          amount              NUMERIC(10,2) NOT NULL,
                          method              VARCHAR(20)   NOT NULL
                              CHECK (method IN ('PAYHERE', 'CASH', 'CARD')),
                          status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
                          payhere_order_id    VARCHAR(255),
                          payhere_payment_id  VARCHAR(255),
                          paid_at             TIMESTAMP,
                          created_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_payments_payhere_order_id ON payments(payhere_order_id);

ALTER TABLE orders
    ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'PAYHERE'
        CHECK (payment_method IN ('PAYHERE', 'CASH', 'CARD'));