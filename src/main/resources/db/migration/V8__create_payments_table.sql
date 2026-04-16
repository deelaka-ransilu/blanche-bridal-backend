CREATE TABLE payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID REFERENCES orders(id) ON DELETE CASCADE,
    amount              DECIMAL(10,2) NOT NULL,
    method              VARCHAR(20) NOT NULL CHECK (method IN ('PAYHERE','CASH','CARD')),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','COMPLETED','FAILED','REFUNDED')),
    payhere_order_id    VARCHAR(255),
    payhere_payment_id  VARCHAR(255),
    paid_at             TIMESTAMP,
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE TABLE receipts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id       UUID REFERENCES orders(id) ON DELETE CASCADE,
    payment_id     UUID REFERENCES payments(id) ON DELETE CASCADE,
    receipt_number VARCHAR(50) UNIQUE NOT NULL,
    pdf_url        VARCHAR(500),
    issued_at      TIMESTAMP DEFAULT NOW()
);